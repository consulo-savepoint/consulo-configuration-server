package org.jetbrains.plugins.ideaConfigurationServer.actions;

import org.jetbrains.plugins.ideaConfigurationServer.IcsSettingsPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;

class ConfigureIcsAction extends DumbAwareAction
{
	@Override
	public void actionPerformed(AnActionEvent e)
	{
		new IcsSettingsPanel(e.getData(PlatformDataKeys.PROJECT)).show();
	}
}
