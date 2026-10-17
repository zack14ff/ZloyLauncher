package org.betacraft.launcher;

import java.io.InputStreamReader;
import java.net.URL;

import javax.swing.JFrame;

import org.betacraft.launcher.Release.VersionRepository;

import uk.betacraft.json.lib.LaunchMethod;

public class StartThread extends Thread {

	public void run() {
		new Window();
		Window.mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		Lang.refresh(true, true);
		
		// thread authenticate + login
		new Thread(new Runnable() {
			public void run() {
				// No self-update here: the upstream update server would replace this launcher with BetaCraft

				if (!Launcher.auth.authenticate()) {
					// Keep the licensed account in the list (it may be just a network hiccup)
					// and play on an offline account for now
					AccountManager.fallbackToOffline();
				}
			}
		}).start();

		try {
			Release.loadVersions(VersionRepository.BETACRAFT);
			org.betacraft.launcher.Addon.loadAddons();
			ModsRepository.loadMods();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		Window.mainWindow.setVisible(true);
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				Window.offerShortcuts();
			}
		});
		Launcher.totalThreads.remove(this);
	}
}
