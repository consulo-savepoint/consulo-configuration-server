package org.jetbrains.plugins.ideaConfigurationServer;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(name = "IcsProjectId", roamingType = RoamingType.DISABLED, storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class ProjectId implements PersistentStateComponent<ProjectId>
{
	public String uid;
	public String path;

	@Nullable
	@Override
	public ProjectId getState()
	{
		return this;
	}

	@Override
	public void loadState(ProjectId state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}