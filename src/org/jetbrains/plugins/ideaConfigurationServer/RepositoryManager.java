package org.jetbrains.plugins.ideaConfigurationServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;

public interface RepositoryManager
{
	@Nullable
	String getRemoteRepositoryUrl();

	ActionCallback cloneFromRemote(String url, ProgressIndicator progressIndicator);

	ActionCallback initLocal(String url, ProgressIndicator progressIndicator);

	@Nullable
	InputStream read(@NotNull String path) throws IOException;

	/**
	 * @param async Write postpone or immediately
	 */
	void write(@NotNull String path, @NotNull byte[] content, int size, boolean async);

	void deleteAsync(@NotNull String path);

	@NotNull
	Collection<String> listSubFileNames(@NotNull String path);

	void updateRepository();

	@NotNull
	ActionCallback commit();

	void commit(@NotNull List<String> paths);

	@NotNull
	ActionCallback push(@NotNull ProgressIndicator indicator);

	@NotNull
	ActionCallback pull(@NotNull ProgressIndicator indicator);

	boolean has(String path);

	void drop();

	boolean isValid();
}