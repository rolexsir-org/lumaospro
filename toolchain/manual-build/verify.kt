import com.android.apksig.ApkVerifier
import java.io.File

fun main(args: Array<String>) {
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
