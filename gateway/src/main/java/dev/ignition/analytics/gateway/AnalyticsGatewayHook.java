package dev.ignition.analytics.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.ignition.analytics.common.ModuleConstants;
import dev.ignition.analytics.gateway.scripting.AnalyticsScriptModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Entry point for the Analytics module in gateway scope.
 * Registers the system.analytics scripting namespace on startup.
 */
public class AnalyticsGatewayHook extends AbstractGatewayModuleHook {

    private final Logger log = LoggerFactory.getLogger(AnalyticsGatewayHook.class);

    private GatewayContext context;
    private AnalyticsScriptModule scriptModule;

    @Override
    public void setup(GatewayContext context) {
        this.context = context;
        log.info("Setting up Ignition Analytics module");
    }

    @Override
    public void startup(LicenseState licenseState) {
        log.info("Starting Ignition Analytics module v{}", getVersion());
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        scriptModule = new AnalyticsScriptModule();
        manager.addScriptModule(
            ModuleConstants.SCRIPT_NAMESPACE,
            scriptModule,
            new PropertiesFileDocProvider()
        );

        log.info("Registered scripting namespace: {}", ModuleConstants.SCRIPT_NAMESPACE);
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Ignition Analytics module");
        scriptModule = null;
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }

    @Override
    public Optional<String> getMountedResourceFolder() {
        return Optional.empty();
    }

    private String getVersion() {
        return Optional.ofNullable(
            getClass().getPackage().getImplementationVersion()
        ).orElse("dev");
    }
}
