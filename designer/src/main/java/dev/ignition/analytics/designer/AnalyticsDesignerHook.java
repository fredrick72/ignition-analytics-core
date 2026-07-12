package dev.ignition.analytics.designer;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import dev.ignition.analytics.common.ModuleConstants;
import dev.ignition.analytics.gateway.scripting.AnalyticsScriptModule;

/**
 * Registers system.analytics in the Designer scope so the Script Console
 * and all Designer scripting can call analytics functions directly.
 */
public class AnalyticsDesignerHook extends AbstractDesignerModuleHook {

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);
        manager.addScriptModule(
            ModuleConstants.SCRIPT_NAMESPACE,
            new AnalyticsScriptModule(),
            new PropertiesFileDocProvider()
        );
    }
}
