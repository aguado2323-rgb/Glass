package com.glass.tv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Datos de la cuenta Xtream. Solo existen descifrados en memoria mientras la app está abierta.
 */
@Serializable
data class Account(val server: String, val user: String, val pass: String) {
    /** Servidor sin barra final, p. ej. https://host:8443 */
    val base: String get() = server.trim().trimEnd('/')
}

/**
 * Almacén cifrado con una clave AES-256-GCM que vive en el Android Keystore.
 * La clave no se puede extraer del dispositivo: aunque alguien copie los datos de la app,
 * no puede descifrar el servidor, el usuario ni la contraseña.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // La clave del Keystore se busca una vez: abrir el Keystore en cada lectura es lento (va por IPC)
    @Volatile private var cachedKey: SecretKey? = null

    @Synchronized
    private fun key(): SecretKey = cachedKey ?: loadKey().also { cachedKey = it }

    init {
        // Si hay datos guardados, se comprueba ya que su clave sigue existiendo (ver loadKey)
        if (prefs.all.isNotEmpty()) try { key() } catch (_: Exception) { }
    }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        // Sin clave pero con datos guardados: el Keystore la perdió y lo cifrado ya no se puede leer.
        // Se borra para empezar limpio (volver a iniciar sesión) en vez de quedar bloqueado por un PIN ilegible.
        if (prefs.all.isNotEmpty()) prefs.edit().clear().commit()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    private fun get(name: String): String? {
        val packed = prefs.getString(name, null) ?: return null
        return try {
            val (ivB, ctB) = packed.split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivB, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ctB, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun loadAccount(): Account? = get(K_ACCOUNT)?.let {
        try { json.decodeFromString<Account>(it) } catch (e: Exception) { null }
    }

    fun saveAccount(a: Account?) = put(K_ACCOUNT, a?.let { json.encodeToString(it) })

    /** Otros secretos (addons con clave, cuentas debrid): cifrados igual que la cuenta. */
    fun saveSecret(name: String, value: String?) = put("x_$name", value)
    fun loadSecret(name: String): String? = get("x_$name")

    /**
     * Hay PIN guardado, aunque no se pueda descifrar. Antes, si el Keystore perdía la clave (actualización del
     * sistema, restauración…), hasPin() decía que no había PIN y checkPin() aceptaba cualquiera: el bloqueo
     * de carpetas y de Ajustes quedaba abierto sin avisar.
     */
    fun hasPin(): Boolean = prefs.contains(K_PIN)

    fun setPin(pin: String?) {
        if (pin == null) { put(K_PIN, null); return }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        put(K_PIN, Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + hash(salt, pin))
    }

    fun checkPin(pin: String): Boolean {
        if (!prefs.contains(K_PIN)) return true
        // PIN guardado pero ilegible (dato dañado o manipulado): se niega en vez de dejar pasar a cualquiera.
        val stored = get(K_PIN) ?: return false
        val parts = stored.split(":", limit = 2)
        if (parts.size != 2) return false
        return try {
            val expected = Base64.decode(parts[1], Base64.NO_WRAP)
            val actual = Base64.decode(hash(Base64.decode(parts[0], Base64.NO_WRAP), pin), Base64.NO_WRAP)
            MessageDigest.isEqual(expected, actual)
        } catch (e: Exception) {
            false
        }
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var d = salt + pin.toByteArray()
        repeat(20000) { d = md.digest(d) }
        return Base64.encodeToString(d, Base64.NO_WRAP)
    }

    fun wipe() = prefs.edit().clear().apply()

    companion object {
        private const val ALIAS = "glass_vault_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val K_ACCOUNT = "account"
        private const val K_PIN = "pin"
    }
}
