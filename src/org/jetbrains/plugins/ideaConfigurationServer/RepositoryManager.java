package org.jetbrains.plugins.ideaConfigurationServer;

import java.io.File;
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

	void setRemoteRepositoryUrl(@Nullable String url);

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

	void initRepository(@NotNull File dir) throws IOException;

	boolean has(String path);
}