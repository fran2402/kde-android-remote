package io.github.fran2402.kderemote.kdeconnect

import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/** This phone's KDE Connect identity: device id, name and the self-signed TLS certificate. */
class LocalIdentity private constructor(
    val deviceId: String,
    val deviceName: String,
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {

    val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(arrayOf(SingleKeyManager()), arrayOf(AcceptAllTrustManager), SecureRandom())
        }
    }

    fun identityPacket(tcpPort: Int): NetworkPacket = NetworkPacket(
        PacketType.IDENTITY,
        JSONObject()
            .put("deviceId", deviceId)
            .put("deviceName", deviceName)
            .put("deviceType", "phone")
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("incomingCapabilities", JSONArray(INCOMING_CAPABILITIES))
            .put("outgoingCapabilities", JSONArray(OUTGOING_CAPABILITIES))
            .put("tcpPort", tcpPort),
    )

    /** Always presents our certificate, whatever the peer asks for. */
    private inner class SingleKeyManager : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getCertificateChain(alias: String?) = arrayOf(certificate)
        override fun getPrivateKey(alias: String?) = privateKey
    }

    /**
     * KDE Connect uses self-signed certificates and trust-on-first-pair, so the TLS layer accepts any
     * certificate; [KdeConnectManager] pins the peer's certificate once the user has paired.
     */
    private object AcceptAllTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val PROTOCOL_VERSION = 8
        private const val ALIAS = "kdeconnect"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_PRIVATE_KEY = "privateKey"
        private const val KEY_CERTIFICATE = "certificate"

        val INCOMING_CAPABILITIES = listOf(
            PacketType.MPRIS,
            PacketType.SYSTEMVOLUME,
            PacketType.RUNCOMMAND,
        )
        val OUTGOING_CAPABILITIES = listOf(
            PacketType.MOUSEPAD_REQUEST,
            PacketType.MPRIS_REQUEST,
            PacketType.SYSTEMVOLUME_REQUEST,
            PacketType.RUNCOMMAND_REQUEST,
        )

        private val DEVICE_ID_REGEX = Regex("^[a-zA-Z0-9_]{32,38}$")

        fun isValidDeviceId(id: String) = DEVICE_ID_REGEX.matches(id)

        /** The desktop rejects names with these characters or longer than 32 characters. */
        fun sanitizeDeviceName(name: String): String =
            name.filterNot { it in "\"',;:.!?()[]<>" }.trim().take(32).ifBlank { "Android remote" }

        fun loadOrCreate(store: KeyValueStore, deviceName: String): LocalIdentity {
            val name = sanitizeDeviceName(deviceName)
            val id = store.get(KEY_DEVICE_ID)
            val key = store.get(KEY_PRIVATE_KEY)
            val cert = store.get(KEY_CERTIFICATE)
            if (id != null && key != null && cert != null) {
                runCatching {
                    return LocalIdentity(id, name, decodePrivateKey(key), decodeCertificate(Base64.getDecoder().decode(cert)))
                }
            }
            val newId = UUID.randomUUID().toString().replace("-", "")
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val subject = X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, newId)
                .addRDN(BCStyle.OU, "KDE Connect")
                .addRDN(BCStyle.O, "KDE")
                .build()
            val now = System.currentTimeMillis()
            val year = 365L * 24 * 60 * 60 * 1000
            val holder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE),
                Date(now - year),
                Date(now + 10 * year),
                subject,
                keyPair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
            val certificate = JcaX509CertificateConverter().getCertificate(holder)
            store.put(KEY_DEVICE_ID, newId)
            store.put(KEY_PRIVATE_KEY, Base64.getEncoder().encodeToString(keyPair.private.encoded))
            store.put(KEY_CERTIFICATE, Base64.getEncoder().encodeToString(certificate.encoded))
            return LocalIdentity(newId, name, keyPair.private, certificate)
        }

        private fun decodePrivateKey(base64: String): PrivateKey =
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))

        fun decodeCertificate(der: ByteArray): X509Certificate =
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }
}
