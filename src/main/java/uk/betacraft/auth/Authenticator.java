package uk.betacraft.auth;

import org.betacraft.launcher.Window;

public abstract class Authenticator {

	//public boolean refreshToken();

	public abstract boolean authenticate();

	public abstract boolean invalidate();

	public abstract Credentials getCredentials();

	public void authSuccess() {
		Window.onAccountChanged();
	}
}
