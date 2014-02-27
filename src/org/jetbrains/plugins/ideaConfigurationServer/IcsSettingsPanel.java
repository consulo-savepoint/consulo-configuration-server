package org.jetbrains.plugins.ideaConfigurationServer;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Consumer;
import com.intellij.util.io.URLUtil;

@SuppressWarnings("DialogTitleCapitalization")
public class IcsSettingsPanel extends DialogWrapper
{
	private JPanel panel;
	private JTextField urlTextField;
	private JCheckBox updateRepositoryFromRemoteCheckBox;
	private JCheckBox shareProjectWorkspaceCheckBox;
	private final JButton syncButton;

	public IcsSettingsPanel()
	{
		super(true);

		IcsManager icsManager = IcsManager.getInstance();
		IcsSettings settings = icsManager.getSettings();

		updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
		shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
		urlTextField.setText(icsManager.getRepositoryManager().getRemoteRepositoryUrl());

		// todo TextComponentUndoProvider should not depends on app settings
		//new TextComponentUndoProvider(urlTextField);

		syncButton = new JButton(IcsBundle.message("settings.panel.syncNow"));
		syncButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if(!saveRemoteRepositoryUrl())
				{
					return;
				}

				IcsManager.getInstance().sync().doWhenDone(new Runnable()
				{
					@Override
					public void run()
					{
						Messages.showInfoMessage(getContentPane(), IcsBundle.message("sync.done.message"), IcsBundle.message("sync.done.title"));
					}
				}).doWhenRejected(new Consumer<String>()
				{
					@Override
					public void consume(String error)
					{
						Messages.showErrorDialog(getContentPane(), IcsBundle.message("sync.rejected.message", StringUtil.notNullize(error,
								"Internal error")), IcsBundle.message("sync.rejected.title"));
					}
				});
			}
		});
		updateSyncButtonState();

		urlTextField.getDocument().addDocumentListener(new DocumentAdapter()
		{
			@Override
			protected void textChanged(DocumentEvent e)
			{
				updateSyncButtonState();
			}
		});

		setTitle(IcsBundle.message("settings.panel.title"));
		setResizable(false);
		init();
	}

	private void updateSyncButtonState()
	{
		String url = urlTextField.getText();
		syncButton.setEnabled(!StringUtil.isEmptyOrSpaces(url) && url.length() > 1);
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
		return new Action[]{getOKAction()};
	}

	@Override
	protected void doOKAction()
	{
		apply();
		super.doOKAction();
	}

	@Nullable
	@Override
	protected JComponent createSouthPanel()
	{
		JComponent southPanel = super.createSouthPanel();
		assert southPanel != null;
		southPanel.add(syncButton, BorderLayout.WEST);
		return southPanel;
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
		String url = StringUtil.nullize(urlTextField.getText());
		if(url != null)
		{
			boolean isFile;
			if(url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX))
			{
				url = url.substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length());
				isFile = true;
			}
			else
			{
				isFile = !URLUtil.containsScheme(url);
			}

			if(isFile)
			{
				File file = new File(url);
				if(file.exists())
				{
					if(!file.isDirectory())
					{
						Messages.showErrorDialog(getContentPane(), "Specified path is not a directory", "Specified path is invalid");
						return false;
					}
				}
				else if(Messages.showYesNoDialog(getContentPane(), IcsBundle.message("init.dialog.message"), IcsBundle.message("init.dialog.title"),
						Messages.getQuestionIcon()) == 0)
				{
					try
					{
						IcsManager.getInstance().getRepositoryManager().initRepository(file);
					}
					catch(IOException e)
					{
						Messages.showErrorDialog(getContentPane(), IcsBundle.message("init.failed.message", e.getMessage()),
								IcsBundle.message("init.failed.title"));
						return false;
					}
				}
				else
				{
					return false;
				}
			}
		}
		IcsManager.getInstance().getRepositoryManager().setRemoteRepositoryUrl(url);
		return true;
	}
}