package org.jetbrains.plugins.ideaConfigurationServer;

import java.awt.event.MouseEvent;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;

public class IcsStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation
{
	@NotNull
	@Override
	public String ID()
	{
		return IcsManager.PLUGIN_NAME;
	}

	@Override
	public WidgetPresentation getPresentation(@NotNull PlatformType type)
	{
		return this;
	}

	@Override
	public void install(@NotNull final StatusBar statusBar)
	{
		ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(StatusListener.TOPIC, new StatusListener()
		{
			@Override
			public void statusChanged(IcsStatus status)
			{
				Application app = ApplicationManager.getApplication();
				if(app.isDispatchThread())
				{
					statusBar.updateWidget(ID());
				}
				else
				{
					app.invokeLater(new Runnable()
					{
						@Override
						public void run()
						{
							statusBar.updateWidget(ID());
						}
					});
				}
			}
		});
	}

	@Override
	public void dispose()
	{
	}

	@NotNull
	@Override
	public Icon getIcon()
	{
		return getStatusIcon(IcsManager.getInstance().getStatus());
	}

	@Override
	public String getTooltipText()
	{
		return IcsManager.PLUGIN_NAME + " status: " + IcsManager.getInstance().getStatusText();
	}

	@Override
	public Consumer<MouseEvent> getClickConsumer()
	{
		return new Consumer<MouseEvent>()
		{
			@Override
			public void consume(MouseEvent event)
			{
				new IcsSettingsPanel().show();
			}
		};
	}

	private static Icon getStatusIcon(@NotNull IcsStatus status)
	{
		switch(status)
		{
			case OPEN_FAILED:
			case UPDATE_FAILED:
				return AllIcons.Nodes.ExceptionClass;
			default:
				return AllIcons.Nodes.Read_access;
		}
	}
}