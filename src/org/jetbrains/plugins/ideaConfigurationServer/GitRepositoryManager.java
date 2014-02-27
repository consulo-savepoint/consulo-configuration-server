package org.jetbrains.plugins.ideaConfigurationServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.UIUtil;
import lombok.val;

public final class GitRepositoryManager extends BaseRepositoryManager
{
	private Git myGitWrapper;

	private CredentialsProvider credentialsProvider;

	public GitRepositoryManager()
	{
	}

	private Git getGit()
	{
		if(!dir.exists() || !new File(dir, ".git").exists())
		{
			return null;
		}

		try
		{
			myGitWrapper = Git.open(dir);
		}
		catch(IOException e)
		{
		}
		return myGitWrapper;
	}

	@Override
	public boolean isValid()
	{
		return getGit() != null;
	}

	@Override
	public ActionCallback initLocal(@NotNull final String url, @NotNull ProgressIndicator progressIndicator)
	{
		myGitWrapper = null;
		return execute(new ThrowableConsumer<ProgressIndicator, Exception>()
		{
			@Override
			public void consume(ProgressIndicator progressIndicator) throws Exception
			{
				if(dir.exists())
				{
					FileUtil.delete(dir);
				}

				new FileRepositoryBuilder().setBare().setGitDir(dir).build().create(true);

				Git git = getGit();

				assert git != null;

				StoredConfig config = git.getRepository().getConfig();
				if(StringUtil.isEmptyOrSpaces(url))
				{
					config.unset(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
				}
				else
				{
					config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL, url);
					config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch",
							"+refs/heads/*:refs/remotes/origin/*");
					config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF,
							ConfigConstants.CONFIG_KEY_FALSE);
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_REMOTE,
							Constants.DEFAULT_REMOTE_NAME);
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE, "refs/heads/master");
				}

				try
				{
					config.save();
					IcsManager.getInstance().setStatus(IcsStatus.OPENED);
				}
				catch(IOException e)
				{
					LOG.error(e);
				}
			}
		}, progressIndicator);
	}

	@Override
	public ActionCallback cloneFromRemote(final String url, ProgressIndicator progressIndicator)
	{
		myGitWrapper = null;
		return execute(new ThrowableConsumer<ProgressIndicator, Exception>()
		{
			@Override
			public void consume(ProgressIndicator progressIndicator) throws Exception
			{
				if(dir.exists())
				{
					FileUtil.delete(dir);
				}

				progressIndicator.setText("Cloning");
				progressIndicator.setIndeterminate(true);
				Git.cloneRepository().setDirectory(dir).setURI(url)
						.setCredentialsProvider(getCredentialsProvider()).call();
			}
		}, progressIndicator);
	}

	private CredentialsProvider getCredentialsProvider()
	{
		if(credentialsProvider == null)
		{
			credentialsProvider = new MyCredentialsProvider();
		}
		return credentialsProvider;
	}

	@Nullable
	@Override
	public String getRemoteRepositoryUrl()
	{
		Git git = getGit();
		if(git == null)
		{
			return null;
		}
		return StringUtil.nullize(git.getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME,
				ConfigConstants.CONFIG_KEY_URL));
	}

	@Override
	protected void doUpdateRepository() throws Exception
	{
		Git git = getGit();
		if(git == null)
		{
			return;
		}
		git.fetch().setRemoveDeletedRefs(true).setCredentialsProvider(getCredentialsProvider()).call();
	}

	@Override
	protected boolean hasRemoteRepository()
	{
		Git git = getGit();
		if(git == null)
		{
			return false;
		}
		return !StringUtil.isEmptyOrSpaces(git.getRepository().getConfig().getString("remote", "origin", "url"));
	}

	@Override
	protected void doAdd(String path) throws Exception
	{
		Git git = getGit();
		if(git == null)
		{
			return;
		}

		git.add().addFilepattern(path).call();
	}

	@Override
	protected void doDelete(@NotNull String path) throws GitAPIException
	{
		Git git = getGit();
		if(git == null)
		{
			return;
		}

		git.rm().addFilepattern(path).call();
	}

	@NotNull
	@Override
	public ActionCallback commit()
	{
		val git = getGit();
		if(git == null)
		{
			return ActionCallback.REJECTED;
		}

		return execute(new ThrowableConsumer<ProgressIndicator, Exception>()
		{
			@Override
			public void consume(@NotNull ProgressIndicator indicator) throws Exception
			{
				IndexDiff index = new IndexDiff(git.getRepository(), Constants.HEAD, new FileTreeIterator(git.getRepository()));
				// don't worry about untracked/modified only in the FS files
				if(!index.diff() || (index.getAdded().isEmpty() && index.getChanged().isEmpty() && index.getRemoved().isEmpty()))
				{
					if(index.getModified().isEmpty())
					{
						LOG.debug("skip scheduled commit, nothing to commit");
						return;
					}

					AddCommand addCommand = git.add();
					boolean added = false;
					for(String path : index.getModified())
					{
						// todo is path absolute or relative?
						if(!path.startsWith(IcsUrlBuilder.PROJECTS_DIR_NAME))
						{
							addCommand.addFilepattern(path);
							added = true;
						}
					}
					if(added)
					{
						addCommand.call();
					}
				}

				PersonIdent author = new PersonIdent(git.getRepository());
				PersonIdent committer = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), author.getEmailAddress());
				git.commit().setAuthor(author).setCommitter(committer).setMessage("").call();
			}
		}, new EmptyProgressIndicator());
	}

	@Override
	public void commit(@NotNull List<String> paths)
	{

	}

	@Override
	@NotNull
	public ActionCallback push(@NotNull final ProgressIndicator indicator)
	{
		val git = getGit();
		if(git == null)
		{
			return ActionCallback.REJECTED;
		}

		return execute(new ThrowableConsumer<ProgressIndicator, Exception>()
		{
			@Override
			public void consume(ProgressIndicator indicator) throws Exception
			{
				git.push().setProgressMonitor(new JGitProgressMonitor(indicator)).setCredentialsProvider(getCredentialsProvider()).call();
			}
		}, indicator);
	}

	@Override
	@NotNull
	public ActionCallback pull(@NotNull final ProgressIndicator indicator)
	{
		val git = getGit();
		if(git == null)
		{
			return ActionCallback.REJECTED;
		}

		return execute(new ThrowableConsumer<ProgressIndicator, Exception>()
		{
			@Override
			public void consume(@NotNull ProgressIndicator indicator) throws Exception
			{
				JGitProgressMonitor progressMonitor = new JGitProgressMonitor(indicator);
				FetchResult fetchResult = git.fetch().setRemoveDeletedRefs(true).setProgressMonitor(progressMonitor).setCredentialsProvider
						(getCredentialsProvider()).call();
				if(LOG.isDebugEnabled())
				{
					String messages = fetchResult.getMessages();
					if(!StringUtil.isEmptyOrSpaces(messages))
					{
						LOG.debug(messages);
					}
				}

				Iterator<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates().iterator();
				TrackingRefUpdate refUpdate = refUpdates.hasNext() ? refUpdates.next() : null;
				if(refUpdate == null || refUpdate.getResult() == RefUpdate.Result.NO_CHANGE)
				{
					// nothing to merge
					return;
				}

				int attemptCount = 0;
				do
				{
					MergeCommand mergeCommand = git.merge();
					org.eclipse.jgit.lib.Ref ref = getUpstreamBranchRef();
					if(ref == null)
					{
						throw new AssertionError();
					}
					else
					{
						mergeCommand.include(ref);
					}

					MergeResult mergeResult = mergeCommand.setFastForward(MergeCommand.FastForwardMode.FF_ONLY).call();
					if(LOG.isDebugEnabled())
					{
						LOG.debug(mergeResult.toString());
					}

					MergeResult.MergeStatus status = mergeResult.getMergeStatus();
					if(status.isSuccessful())
					{
						rebase(progressMonitor);
						return;
					}
					else if(status != MergeResult.MergeStatus.ABORTED)
					{
						break;
					}
				}
				while(++attemptCount < 3);
			}

			private org.eclipse.jgit.lib.Ref getUpstreamBranchRef() throws IOException
			{
				return git.getRepository().getRef(Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
			}

			private void rebase(@NotNull JGitProgressMonitor progressMonitor) throws GitAPIException
			{
				RebaseResult result = null;
				do
				{
					if(result == null)
					{
						result = git.rebase().setUpstream(Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER).setProgressMonitor
								(progressMonitor).call();
					}
					else if(result.getStatus() == RebaseResult.Status.CONFLICTS)
					{
						throw new UnsupportedOperationException();
					}
					else if(result.getStatus() == RebaseResult.Status.NOTHING_TO_COMMIT)
					{
						result = git.rebase().setOperation(RebaseCommand.Operation.SKIP).call();
					}
					else
					{
						throw new UnsupportedOperationException();
					}
				}
				while(!result.getStatus().isSuccessful());
			}
		}, indicator);
	}

	@Override
	public void drop()
	{
		FileUtil.delete(dir);
	}

	private static class JGitProgressMonitor implements ProgressMonitor
	{
		private final ProgressIndicator indicator;

		public JGitProgressMonitor(ProgressIndicator indicator)
		{
			this.indicator = indicator;
		}

		@Override
		public void start(int totalTasks)
		{
		}

		@Override
		public void beginTask(String title, int totalWork)
		{
			indicator.setText2(title);
		}

		@Override
		public void update(int completed)
		{
			// todo
		}

		@Override
		public void endTask()
		{
			indicator.setText2("");
		}

		@Override
		public boolean isCancelled()
		{
			return indicator.isCanceled();
		}
	}

	private class MyCredentialsProvider extends CredentialsProvider
	{
		private String username;
		private String password;

		private MyCredentialsProvider()
		{
			File loginDataFile = getPasswordStorageFile();
			if(loginDataFile.exists())
			{
				try
				{
					boolean hasErrors = true;
					DataInputStream in = new DataInputStream(new FileInputStream(loginDataFile));
					try
					{
						username = PasswordUtil.decodePassword(IOUtil.readString(in));
						password = PasswordUtil.decodePassword(IOUtil.readString(in));
						hasErrors = false;
					}
					finally
					{
						if(hasErrors)
						{
							//noinspection ResultOfMethodCallIgnored
							loginDataFile.delete();
						}
						in.close();
					}
				}
				catch(IOException e)
				{
					LOG.error(e);
				}
			}
		}

		private File getPasswordStorageFile()
		{
			return new File(IcsManager.getPluginSystemDir(), ".git_auth");
		}

		@Override
		public boolean isInteractive()
		{
			return true;
		}

		@Override
		public boolean supports(CredentialItem... items)
		{
			for(CredentialItem item : items)
			{
				if(item instanceof CredentialItem.Password)
				{
					continue;
				}
				if(item instanceof CredentialItem.Username)
				{
					continue;
				}
				return false;
			}
			return true;
		}

		@Override
		public boolean get(final URIish uri, CredentialItem... items) throws UnsupportedCredentialItem
		{
			CredentialItem.Username userNameItem = null;
			CredentialItem.Password passwordItem = null;
			for(CredentialItem item : items)
			{
				if(item instanceof CredentialItem.Username)
				{
					userNameItem = (CredentialItem.Username) item;
				}
				else if(item instanceof CredentialItem.Password)
				{
					passwordItem = (CredentialItem.Password) item;
				}
			}

			if(userNameItem != null || passwordItem != null)
			{
				String u = uri.getUser();
				String p;
				if(u == null)
				{
					// username is not in the url - reading pre-filled value from the password storage
					u = username;
					p = password;
				}
				else
				{
					p = StringUtil.nullize(uri.getPass(), true);
					// username is in url - read password only if it is for the same user
					if(u.equals(username) && p == null)
					{
						p = password;
					}
				}

				boolean ok;
				if(u != null && p != null)
				{
					ok = true;
				}
				else
				{
					final Ref<AuthDialog> dialogRef = Ref.create();
					final String finalU = u;
					final String finalP = p;
					UIUtil.invokeAndWaitIfNeeded(new Runnable()
					{
						@Override
						public void run()
						{
							AuthDialog dialog = new AuthDialog("Login required", "Login to " + uri, finalU, finalP);
							dialogRef.set(dialog);
							dialog.show();
						}
					});
					ok = dialogRef.get().isOK();
					if(ok)
					{
						u = dialogRef.get().getUsername();
						p = dialogRef.get().getPassword();
						if(StringUtil.isEmptyOrSpaces(p))
						{
							p = "x-oauth-basic";
						}
					}
				}

				if(ok)
				{
					if(userNameItem != null)
					{
						userNameItem.setValue(u);
					}
					if(passwordItem != null)
					{
						passwordItem.setValue(p.toCharArray());
					}
					password = p;
					username = u;

					taskProcessor.add(new ThrowableRunnable<Exception>()
					{
						@Override
						public void run() throws Exception
						{
							File loginDataFile = getPasswordStorageFile();
							FileUtil.createParentDirs(loginDataFile);
							DataOutputStream out = new DataOutputStream(new FileOutputStream(loginDataFile));
							try
							{
								IOUtil.writeString(PasswordUtil.encodePassword(username), out);
								IOUtil.writeString(PasswordUtil.encodePassword(password), out);
							}
							finally
							{
								out.close();
							}
						}
					});
				}
				return ok;
			}
			return true;
		}
	}
}