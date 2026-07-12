import java.util.Properties

plugins {
    id("io.ia.sdk.modl") version("0.1.1")
}

val sdk_version by extra("8.3.0")

// If signing/signing.properties exists (never committed — see signing/README.md),
// load the ignition.signing.* keys it defines and sign the module with them.
// Otherwise fall back to an unsigned build so a fresh clone/fork keeps working
// without any signing credentials.
val signingPropsFile = rootProject.file("signing/signing.properties")
val signingConfigured = signingPropsFile.exists()
if (signingConfigured) {
    val signingProps = Properties().apply { signingPropsFile.inputStream().use { load(it) } }
    signingProps.forEach { (key, value) -> extra[key.toString()] = value.toString() }
}

allprojects {
    version = "0.1.0"
    group = "dev.ignition.analytics"
}

ignitionModule {
    name.set("Ignition Analytics")
    fileName.set("ignition-analytics")
    id.set("dev.ignition.analytics")
    moduleVersion.set("${project.version}")
    moduleDescription.set("Modern data analytics for Ignition: time series, statistics, anomaly detection, and forecasting.")
    requiredIgnitionVersion.set("8.3.0")
    license.set("LICENSE.html")

    projectScopes.putAll(
        mapOf(
            ":common" to "GCD",
            ":gateway" to "G",
            ":designer" to "D",
        )
    )

    hooks.putAll(
        mapOf(
            "dev.ignition.analytics.gateway.AnalyticsGatewayHook" to "G",
            "dev.ignition.analytics.designer.AnalyticsDesignerHook" to "D",
        )
    )

    skipModlSigning.set(!signingConfigured)
}
