package org.jetbrains.plugins.ideaConfigurationServer;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

public final class IcsBundle
{
	private static Reference<ResourceBundle> ourBundle;

	@NonNls
	public static final String BUNDLE = "messages.IcsBundle";

	private IcsBundle()
	{
	}

	public static String message(@NonNls @PropertyKey(resourceBundle = BUNDLE) String key, Object... params)
	{
		return CommonBundle.message(getBundle(), key, params);
	}

	private static ResourceBundle getBundle()
	{
		ResourceBundle bundle = null;
		if(ourBundle != null)
		{
			bundle = ourBundle.get();
		}
		if(bundle == null)
		{
			bundle = ResourceBundle.getBundle(BUNDLE);
			ourBundle = new SoftReference<ResourceBundle>(bundle);
		}
		return bundle;
	}
}
