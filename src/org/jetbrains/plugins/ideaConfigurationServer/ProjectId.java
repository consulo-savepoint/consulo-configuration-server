package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(name = "IcsProjectId", roamingType = RoamingType.DISABLED, storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
class ProjectId implements PersistentStateComponent<ProjectId> {
  public String uid;
  public String path;

  public static ProjectId getInstance(Project project) {
    return ServiceManager.getService(project, ProjectId.class);
  }

  @Nullable
  @Override
  public ProjectId getState() {
    return this;
  }

  @Override
  public void loadState(ProjectId state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}