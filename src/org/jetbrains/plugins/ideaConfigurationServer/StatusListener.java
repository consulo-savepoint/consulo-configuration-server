package org.jetbrains.plugins.ideaConfigurationServer;

import java.util.EventListener;

import com.intellij.util.messages.Topic;

public interface StatusListener extends EventListener
{
	Topic<StatusListener> TOPIC = new Topic<StatusListener>("ICS status changes", StatusListener.class);

	void statusChanged(IcsStatus status);
}
