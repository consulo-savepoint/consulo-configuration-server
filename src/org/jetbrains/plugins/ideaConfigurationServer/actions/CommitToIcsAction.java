package org.jetbrains.plugins.ideaConfigurationServer.actions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ideaConfigurationServer.CommitToIcsDialog;
import org.jetbrains.plugins.ideaConfigurationServer.IcsBundle;
import org.jetbrains.plugins.ideaConfigurationServer.IcsManager;
import org.jetbrains.plugins.ideaConfigurationServer.ProjectId;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.actions.CommonCheckinFilesAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.BeforeCheckinDialogHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;

class CommitToIcsAction extends CommonCheckinFilesAction
{
	static class IcsBeforeCommitDialogHandler extends CheckinHandlerFactory
	{
		private static final BeforeCheckinDialogHandler BEFORE_CHECKIN_DIALOG_HANDLER = new BeforeCheckinDialogHandler()
		{
			@Override
			public boolean beforeCommitDialogShown(@NotNull Project project, @NotNull List<Change> changes, @NotNull Iterable<CommitExecutor> executors,
					boolean showVcsCommit)
			{
				ProjectChangeCollectConsumer collectConsumer = new ProjectChangeCollectConsumer(project);
				collectProjectChanges(changes, collectConsumer);
				showDialog(project, collectConsumer, null);
				return true;
			}
		};

		@NotNull
		@Override
		public CheckinHandler createHandler(CheckinProjectPanel panel, CommitContext commitContext)
		{
			return CheckinHandler.DUMMY;
		}

		@Override
		public BeforeCheckinDialogHandler createSystemReadyHandler(Project project)
		{
			return BEFORE_CHECKIN_DIALOG_HANDLER;
		}
	}

	@Override
	protected String getActionName(VcsContext dataContext)
	{
		return IcsBundle.message("action.CommitToIcs.text");
	}

	@Override
	protected boolean isApplicableRoot(VirtualFile file, FileStatus status, VcsContext dataContext)
	{
		return ((ProjectEx) dataContext.getProject()).getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED &&
				super.isApplicableRoot(file, status, dataContext) &&
				!file.isDirectory() &&
				isProjectConfigFile(file, dataContext.getProject());
	}

	private static boolean isProjectConfigFile(@Nullable VirtualFile file, Project project)
	{
		if(file == null)
		{
			return false;
		}

		VirtualFile projectFile = project.getProjectFile();
		VirtualFile projectConfigDir = projectFile == null ? null : projectFile.getParent();
		return projectConfigDir != null && VfsUtilCore.isAncestor(projectConfigDir, file, true);
	}

	@Override
	protected FilePath[] prepareRootsForCommit(FilePath[] roots, Project project)
	{
		return null;
	}

	@Override
	protected void performCheckIn(VcsContext context, Project project, FilePath[] roots)
	{
		String projectId = getProjectId(project);
		if(projectId == null)
		{
			return;
		}

		Change[] changes = context.getSelectedChanges();
		ProjectChangeCollectConsumer collectConsumer = new ProjectChangeCollectConsumer(project);
		if(changes != null && changes.length > 0)
		{
			for(Change change : changes)
			{
				collectConsumer.consume(change);
			}
		}
		else
		{
			ChangeListManager manager = ChangeListManager.getInstance(project);
			FilePath[] paths = getRoots(context);
			for(FilePath path : paths)
			{
				collectProjectChanges(manager.getChangesIn(path), collectConsumer);
			}
		}

		showDialog(project, collectConsumer, projectId);
	}

	@Nullable
	private static String getProjectId(@NotNull Project project)
	{
		ProjectId projectId = ServiceManager.getService(project, ProjectId.class);
		if(projectId.uid == null)
		{
			if(MessageDialogBuilder.yesNo("Settings Server Project Mapping", "Project is not mapped on Settings Server. Would you like to map?").project
					(project).doNotAsk(new DialogWrapper.PropertyDoNotAskOption("")
			{
				@Override
				public void setToBeShown(boolean value, int exitCode)
				{
					IcsManager.getInstance().getSettings().doNoAskMapProject = !value;
				}

				@Override
				public boolean isToBeShown()
				{
					return !IcsManager.getInstance().getSettings().doNoAskMapProject;
				}

				@Override
				public boolean canBeHidden()
				{
					return true;
				}
			}).show() == Messages.YES)
			{
				projectId.uid = UUID.randomUUID().toString();
			}
		}

		return projectId.uid;
	}

	private static void showDialog(Project project, ProjectChangeCollectConsumer collectConsumer, String projectId)
	{
		if(!collectConsumer.hasResult())
		{
			return;
		}

		if(projectId == null)
		{
			projectId = getProjectId(project);
			if(projectId == null)
			{
				return;
			}
		}

		new CommitToIcsDialog(project, projectId, collectConsumer.getResult()).show();
	}

	private static void collectProjectChanges(Collection<Change> changes, ProjectChangeCollectConsumer collectConsumer)
	{
		for(Change change : changes)
		{
			collectConsumer.consume(change);
		}
	}

	private static final class ProjectChangeCollectConsumer implements Consumer<Change>
	{
		private final Project project;
		private List<Change> projectChanges;

		private ProjectChangeCollectConsumer(Project project)
		{
			this.project = project;
		}

		@Override
		public void consume(Change change)
		{
			if(isProjectConfigFile(change.getVirtualFile(), project))
			{
				if(projectChanges == null)
				{
					projectChanges = new SmartList<Change>();
				}
				projectChanges.add(change);
			}
		}

		public List<Change> getResult()
		{
			return projectChanges == null ? Collections.<Change>emptyList() : projectChanges;
		}

		public boolean hasResult()
		{
			return projectChanges != null;
		}
	}
}