import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

fun main(args: Array<String>) {
    if (args.firstOrNull() == "sign") {
        val ksFile = File(args[1])
        val storePass = args[2].toCharArray()
        val alias = args[3]
        val keyPass = args[4].toCharArray()
        val inApk = File(args[5])
        val outApk = File(args[6])
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            ksFile.inputStream().use { load(it, storePass) }
        }
        val key = ks.getKey(alias, keyPass) as PrivateKey
        val certs = ks.getCertificateChain(alias).map { it as X509Certificate }
        val signerConfig = ApkSigner.SignerConfig.Builder(alias, key, certs).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inApk)
            .setOutputApk(outApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
        return
    }
    val apk = File(args[0])
    val result = ApkVerifier.Builder(apk).build().verify()
    println("APK VERIFY: ${apk.name}")
    println("  verified           : ${result.isVerified}")
    println("  verified using v1  : ${result.isVerifiedUsingV1Scheme}")
    println("  verified using v2  : ${result.isVerifiedUsingV2Scheme}")
    println("  verified using v3  : ${result.isVerifiedUsingV3Scheme}")
    result.signerCertificates.forEachIndexed { i, c ->
        println("  signer[$i]         : ${c.subjectX500Principal.name}")
    }
    if (!result.isVerified) {
        result.errors.forEach { println("  ERROR: $it") }
        kotlin.system.exitProcess(1)
    }
}
