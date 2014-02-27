package org.jetbrains.plugins.ideaConfigurationServer;

import java.util.concurrent.Callable;

import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Restarter;
import lombok.val;

@SuppressWarnings("DialogTitleCapitalization")
public class IcsSettingsPanel extends DialogWrapper
{
	@Nullable
	private final Project myProject;
	private JPanel panel;
	private JTextField urlTextField;
	private JCheckBox updateRepositoryFromRemoteCheckBox;
	private JCheckBox shareProjectWorkspaceCheckBox;

	public IcsSettingsPanel(@Nullable Project project)
	{
		super(true);
		myProject = project;

		IcsManager icsManager = IcsManager.getInstance();
		IcsSettings settings = icsManager.getSettings();

		updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
		shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
		urlTextField.setText(icsManager.getRepositoryManager().getRemoteRepositoryUrl());

		setTitle(IcsBundle.message("settings.panel.title"));
		pack();
		init();
	}

	@Nullable
	@Override
	public JComponent getPreferredFocusedComponent()
	{
		return urlTextField;
	}

	@Nullable
	@Override
	protected JComponent createCenterPanel()
	{
		return panel;
	}

	@NotNull
	@Override
	protected Action[] createActions()
	{
		return new Action[]{getOKAction(), getCancelAction()};
	}

	@Override
	protected void doOKAction()
	{
		apply();
		super.doOKAction();
	}

	private void apply()
	{
		IcsSettings settings = IcsManager.getInstance().getSettings();
		settings.updateOnStart = updateRepositoryFromRemoteCheckBox.isSelected();
		settings.shareProjectWorkspace = shareProjectWorkspaceCheckBox.isSelected();
		saveRemoteRepositoryUrl();

		ApplicationManager.getApplication().executeOnPooledThread(new Callable<Object>()
		{
			@Override
			public Object call() throws Exception
			{
				IcsManager.getInstance().getSettings().save();
				return null;
			}
		});
	}

	private boolean saveRemoteRepositoryUrl()
	{
		val icsManager = IcsManager.getInstance();
		val repositoryManager = icsManager.getRepositoryManager();
		val url = StringUtil.nullize(urlTextField.getText());
		if(url != null)
		{
			val i = Messages.showYesNoCancelDialog("Choose initial options for sync. If you what use empty repository, click 'Init', " +
					"if not 'Clone and Restart'", "Configuration Repository", "Init", "Clone and Restart", "Discard", Messages.getQuestionIcon());

			if(i == Messages.CANCEL)
			{
				return false;
			}
			else
			{
				new Task.Backgroundable(myProject, "Preparing")
				{
					@Override
					public void run(@NotNull ProgressIndicator progressIndicator)
					{
						val callback = new ActionCallback();

						if(i == Messages.YES)
						{
							repositoryManager.initLocal(url, progressIndicator).notify(callback);
						}
						else
						{
							ActionCallback actionCallback = repositoryManager.cloneFromRemote(url, progressIndicator);
							actionCallback.notify(callback).doWhenDone(new Runnable()
							{
								@Override
								public void run()
								{
									icsManager.setStatus(IcsStatus.OPEN_FAILED);
									if(Restarter.isSupported())
									{
										final ApplicationImpl app = (ApplicationImpl) ApplicationManager.getApplication();
										app.restart(true);
									}
									else
									{
										Messages.showInfoMessage(myProject, "Auto-restart is not supported. Please restart Consulo", "Information");
									}
								}
							});
						}

						callback.waitFor(-1);
					}
				}.queue();
			}
		}
		else
		{
			repositoryManager.drop();
			icsManager.setStatus(IcsStatus.OPEN_FAILED);
		}
		return url != null;
	}
}