package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The "Instances" tab: a grid of instances with their icons, like the news and server tabs it lives next to */
public class InstancesPanel extends JPanel {
	static final Color SELECTED_FILL = new Color(90, 130, 220, 110);
	static final Color SELECTED_BORDER = new Color(140, 170, 255, 200);
	static final Color HOVER_FILL = new Color(255, 255, 255, 30);
	static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy HH:mm");
	static final String BAD_NAME_CHARS = "\\/:*?\"<>|";

	private static BufferedImage background = null;

	private final JPanel tiles;
	private final JTextField search;
	private final JLabel info, notes;
	private final List<Tile> tileList = new ArrayList<Tile>();

	public InstancesPanel() {
		super(new BorderLayout());
		if (background == null) {
			try {
				background = ImageIO.read(InstancesPanel.class.getResourceAsStream("/icons/news_bg.png"));
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		// toolbar
		JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setOpaque(false);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		buttons.setOpaque(false);
		buttons.add(button(Lang.INSTANCES_NEW, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createInstance();
			}
		}));
		buttons.add(button(Lang.INSTANCES_EDIT, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editInstance(Launcher.currentInstance);
			}
		}));
		buttons.add(button(Lang.INSTANCES_ICON, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeIcon(Launcher.currentInstance);
			}
		}));
		buttons.add(button(Lang.INSTANCES_COPY, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				copyInstance(Launcher.currentInstance);
			}
		}));
		buttons.add(button(Lang.INSTANCES_FOLDER, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Window.openGameDir();
			}
		}));
		buttons.add(button(Lang.REMOVE, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteInstance(Launcher.currentInstance);
			}
		}));
		toolbar.add(buttons, BorderLayout.WEST);

		JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
		searchPanel.setOpaque(false);
		JLabel searchLabel = new JLabel(Lang.INSTANCES_SEARCH);
		searchLabel.setForeground(Color.LIGHT_GRAY);
		searchPanel.add(searchLabel);
		search = new JTextField(12);
		search.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { refresh(); }
			public void removeUpdate(DocumentEvent e) { refresh(); }
			public void changedUpdate(DocumentEvent e) { refresh(); }
		});
		searchPanel.add(search);
		toolbar.add(searchPanel, BorderLayout.EAST);
		this.add(toolbar, BorderLayout.NORTH);

		// the grid
		tiles = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 6));
		tiles.setOpaque(false);
		tiles.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
					JPopupMenu menu = new JPopupMenu();
					menu.add(item(Lang.INSTANCES_NEW, new ActionListener() {
						public void actionPerformed(ActionEvent ev) {
							createInstance();
						}
					}));
					menu.show(tiles, e.getX(), e.getY());
				}
			}
		});
		JScrollPane scroller = new JScrollPane(tiles);
		scroller.setOpaque(false);
		scroller.getViewport().setOpaque(false);
		scroller.setBorder(null);
		scroller.getVerticalScrollBar().setUnitIncrement(24);
		this.add(scroller, BorderLayout.CENTER);

		// details of the selected instance
		JPanel details = new JPanel(new BorderLayout());
		details.setOpaque(false);
		details.setBorder(BorderFactory.createEmptyBorder(2, 10, 4, 10));
		info = new JLabel(" ");
		info.setForeground(Color.LIGHT_GRAY);
		notes = new JLabel(" ");
		notes.setForeground(new Color(0x9C9CFF));
		notes.setFont(notes.getFont().deriveFont(Font.ITALIC));
		details.add(info, BorderLayout.NORTH);
		details.add(notes, BorderLayout.SOUTH);
		this.add(details, BorderLayout.SOUTH);

		refresh();
	}

	protected void paintComponent(Graphics g) {
		if (background == null) {
			g.setColor(new Color(0x202020));
			g.fillRect(0, 0, getWidth(), getHeight());
			return;
		}
		for (int x = 0; x < getWidth(); x += background.getWidth())
			for (int y = 0; y < getHeight(); y += background.getHeight())
				g.drawImage(background, x, y, null);
	}

	private static JButton button(String text, ActionListener l) {
		JButton b = new JButton(text);
		b.setFocusable(false);
		b.addActionListener(l);
		return b;
	}

	private static JMenuItem item(String text, ActionListener l) {
		JMenuItem i = new JMenuItem(text);
		i.addActionListener(l);
		return i;
	}

	/** Re-reads the instances from disk */
	public void refresh() {
		String filter = search.getText().trim().toLowerCase();
		tiles.removeAll();
		tileList.clear();

		List<String> names = Instance.getInstances();
		java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		for (String name : names) {
			Instance i = Instance.loadInstance(name);
			if (i == null) continue;
			if (filter.length() > 0) {
				String hay = (i.name + " " + i.version + " " + i.notes).toLowerCase();
				if (!hay.contains(filter)) continue;
			}
			Tile t = new Tile(i);
			tileList.add(t);
			tiles.add(t);
		}
		if (tileList.isEmpty()) {
			JLabel empty = new JLabel(filter.length() > 0 ? Lang.INSTANCES_NOTHING_FOUND : Lang.INSTANCES_EMPTY);
			empty.setForeground(Color.GRAY);
			empty.setBorder(BorderFactory.createEmptyBorder(30, 20, 0, 0));
			tiles.add(empty);
		}
		updateDetails();
		tiles.revalidate();
		tiles.repaint();
	}

	/** Only the selection changed */
	public void selectionChanged() {
		updateDetails();
		tiles.repaint();
	}

	private void updateDetails() {
		Instance cur = Launcher.currentInstance;
		if (cur == null) return;
		// the current instance object has the freshest data
		StringBuilder b = new StringBuilder();
		b.append(cur.name).append("  —  ").append(cur.version);
		b.append("   |   Java: ").append(javaName(cur.javaPath));
		b.append("   |   ").append(Lang.INSTANCES_LAST_PLAYED).append(' ')
				.append(cur.lastPlayed > 0 ? DATE.format(new Date(cur.lastPlayed)) : Lang.INSTANCES_NEVER);
		b.append("   |   ").append(Lang.INSTANCES_PLAY_TIME).append(' ').append(formatTime(cur.playTime));
		info.setText(b.toString());
		notes.setText(cur.notes == null || cur.notes.length() == 0 ? " " : cur.notes);
	}

	static String javaName(String javaPath) {
		if (javaPath == null) return "?";
		File bin = new File(javaPath).getParentFile();
		if (bin == null || bin.getParentFile() == null) return javaPath;
		return bin.getParentFile().getName();
	}

	public static String formatTime(long seconds) {
		long minutes = seconds / 60;
		if (minutes < 60) return String.format(Lang.TIME_MINUTES, minutes);
		return String.format(Lang.TIME_HOURS_MINUTES, minutes / 60, minutes % 60);
	}

	// ---- actions ----

	private void select(Instance i) {
		if (!i.name.equals(Launcher.currentInstance.name)) {
			Launcher.setInstance(i);
		}
		selectionChanged();
	}

	public static String askName(Component parent, String title, String initial) {
		String name = initial;
		while (true) {
			name = (String) JOptionPane.showInputDialog(parent, Lang.INSTANCE_NAME, title, JOptionPane.PLAIN_MESSAGE, null, null, name);
			if (name == null) return null;
			name = name.trim();
			boolean bad = name.length() == 0 || name.startsWith(".");
			for (char c : BAD_NAME_CHARS.toCharArray()) if (name.indexOf(c) != -1) bad = true;
			if (bad) {
				JOptionPane.showMessageDialog(parent, String.format(Lang.INSTANCES_BAD_NAME, BAD_NAME_CHARS), title, JOptionPane.WARNING_MESSAGE);
				continue;
			}
			boolean taken = false;
			for (String existing : Instance.getInstances()) if (existing.equalsIgnoreCase(name)) taken = true;
			if (taken) {
				JOptionPane.showMessageDialog(parent, Lang.INSTANCES_NAME_TAKEN, title, JOptionPane.WARNING_MESSAGE);
				continue;
			}
			return name;
		}
	}

	public void createInstance() {
		String name = askName(this, Lang.SELECT_INSTANCE_NEW, "");
		if (name == null) return;
		Instance i = Instance.newInstance(name);
		i.saveInstance();
		Launcher.setInstance(i);
		refresh();
		// pick a version right away, like other launchers do
		if (Release.versions.size() > 0) {
			if (Window.versionsList == null) new SelectVersion();
			else Window.versionsList.setVisible(true);
		}
	}

	public static void editInstance(Instance i) {
		if (!i.name.equals(Launcher.currentInstance.name)) Launcher.setInstance(i);
		// always rebuild, an old settings window would still show the previous instance
		if (Window.instanceSettings != null) Window.instanceSettings.dispose();
		Window.instanceSettings = new InstanceSettings();
	}

	public void changeIcon(Instance i) {
		if (IconPicker.pick(this, i)) refresh();
	}

	public void renameInstance(Instance i) {
		String name = askName(this, Lang.INSTANCES_RENAME, i.name);
		if (name == null || name.equals(i.name)) return;
		boolean current = i.name.equals(Launcher.currentInstance.name);
		Instance renamed = i.renameInstance(name);
		renamed.saveInstance();
		if (current) Launcher.setInstance(renamed);
		refresh();
	}

	public void editNotes(Instance i) {
		String text = (String) JOptionPane.showInputDialog(this, Lang.INSTANCES_NOTES_PROMPT, i.name, JOptionPane.PLAIN_MESSAGE, null, null, i.notes);
		if (text == null) return;
		i.notes = text.trim();
		i.saveInstance();
		if (i.name.equals(Launcher.currentInstance.name)) Launcher.currentInstance.notes = i.notes;
		refresh();
	}

	public void copyInstance(final Instance i) {
		final String name = askName(this, Lang.INSTANCES_COPY, i.name + " (2)");
		if (name == null) return;

		final Instance copy = i.clone(name);
		copy.gameDir = BC.get() + name + "/";
		copy.lastPlayed = 0L;
		copy.playTime = 0L;
		copy.saveInstance();
		if (i.getIconFile().isFile()) Util.copy(i.getIconFile(), copy.getIconFile());
		refresh();

		// the game folder (worlds, mods, settings) is copied in the background
		new Thread("Instance-Copy") {
			public void run() {
				copyFolder(new File(i.gameDir), new File(copy.gameDir));
				System.out.println("Copied instance folder of " + i.name + " to " + copy.gameDir);
			}
		}.start();
	}

	static void copyFolder(File from, File to) {
		if (!from.isDirectory()) return;
		to.mkdirs();
		File[] files = from.listFiles();
		if (files == null) return;
		for (File f : files) {
			File target = new File(to, f.getName());
			if (f.isDirectory()) copyFolder(f, target);
			else Util.copy(f, target);
		}
	}

	public void deleteInstance(Instance i) {
		int res = JOptionPane.showConfirmDialog(this, Lang.INSTANCE_REMOVE_QUESTION + "\n" + i.name, Lang.INSTANCE_REMOVE_TITLE, JOptionPane.YES_NO_OPTION);
		if (res != JOptionPane.YES_OPTION) return;
		Launcher.removeInstance(i.name);
		refresh();
	}

	private void showMenu(final Instance i, Component c, int x, int y) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem play = item(Lang.WINDOW_PLAY, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Window.playButton.doClick();
			}
		});
		play.setFont(play.getFont().deriveFont(Font.BOLD));
		menu.add(play);
		menu.addSeparator();
		menu.add(item(Lang.INSTANCES_EDIT, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editInstance(i);
			}
		}));
		menu.add(item(Lang.INSTANCES_ICON, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeIcon(i);
			}
		}));
		menu.add(item(Lang.INSTANCES_RENAME, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				renameInstance(i);
			}
		}));
		menu.add(item(Lang.INSTANCES_NOTES, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editNotes(i);
			}
		}));
		menu.add(item(Lang.INSTANCES_FOLDER, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Window.openFolder(new File(i.gameDir));
			}
		}));
		menu.add(item(Lang.INSTANCES_COPY, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				copyInstance(i);
			}
		}));
		menu.addSeparator();
		menu.add(item(Lang.REMOVE, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteInstance(i);
			}
		}));
		menu.show(c, x, y);
	}

	// ---- one instance in the grid ----

	class Tile extends JComponent {
		static final int W = 112, H = 104, ICON = 48;
		final Instance instance;
		final BufferedImage icon;
		boolean hover = false;

		Tile(final Instance instance) {
			this.instance = instance;
			this.icon = instance.getIcon(ICON);
			this.setPreferredSize(new Dimension(W, H));
			StringBuilder tip = new StringBuilder("<html><b>").append(escape(instance.name)).append("</b><br>")
					.append(escape(instance.version)).append("<br>Java: ").append(escape(javaName(instance.javaPath)));
			if (instance.notes != null && instance.notes.length() > 0) tip.append("<br><i>").append(escape(instance.notes)).append("</i>");
			tip.append("</html>");
			this.setToolTipText(tip.toString());

			this.addMouseListener(new MouseAdapter() {
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				public void mouseExited(MouseEvent e) {
					hover = false;
					repaint();
				}

				public void mousePressed(MouseEvent e) {
					select(instance);
					if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
						showMenu(Launcher.currentInstance, Tile.this, e.getX(), e.getY());
					} else if (e.getClickCount() == 2 && Window.playButton.isEnabled()) {
						Window.playButton.doClick();
					}
				}
			});
		}

		boolean isSelected() {
			return Launcher.currentInstance != null && Launcher.currentInstance.name.equals(instance.name);
		}

		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			if (isSelected()) {
				g.setColor(SELECTED_FILL);
				g.fillRoundRect(1, 1, W - 3, H - 3, 8, 8);
				g.setColor(SELECTED_BORDER);
				g.drawRoundRect(1, 1, W - 3, H - 3, 8, 8);
			} else if (hover) {
				g.setColor(HOVER_FILL);
				g.fillRoundRect(1, 1, W - 3, H - 3, 8, 8);
			}

			g.drawImage(icon, (W - ICON) / 2, 8, null);

			Font nameFont = getFont().deriveFont(Font.BOLD, 12f);
			g.setFont(nameFont);
			g.setColor(Color.WHITE);
			drawCentered(g, ellipsize(instance.name, g.getFontMetrics(), W - 8), 8 + ICON + 16);

			g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
			g.setColor(new Color(0xA0A0A0));
			drawCentered(g, ellipsize(instance.version, g.getFontMetrics(), W - 8), 8 + ICON + 31);
		}

		private void drawCentered(Graphics2D g, String s, int y) {
			int w = g.getFontMetrics().stringWidth(s);
			g.drawString(s, (W - w) / 2, y);
		}
	}

	static String ellipsize(String s, FontMetrics fm, int width) {
		if (s == null) return "";
		if (fm.stringWidth(s) <= width) return s;
		String dots = "...";
		while (s.length() > 0 && fm.stringWidth(s + dots) > width) s = s.substring(0, s.length() - 1);
		return s + dots;
	}

	static String escape(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** FlowLayout that wraps to new rows inside a scroll pane (plain FlowLayout keeps one endless row) */
	static class WrapLayout extends FlowLayout {
		WrapLayout(int align, int hgap, int vgap) {
			super(align, hgap, vgap);
		}

		public Dimension preferredLayoutSize(Container target) {
			return layoutSize(target);
		}

		public Dimension minimumLayoutSize(Container target) {
			Dimension d = layoutSize(target);
			d.width -= getHgap() + 1;
			return d;
		}

		private Dimension layoutSize(Container target) {
			synchronized (target.getTreeLock()) {
				Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
				int targetWidth = scroll != null ? scroll.getWidth() : target.getWidth();
				if (targetWidth <= 0) targetWidth = Integer.MAX_VALUE;

				Insets insets = target.getInsets();
				int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2) - 20;
				int width = 0, height = 0, rowWidth = 0, rowHeight = 0;

				for (Component c : target.getComponents()) {
					if (!c.isVisible()) continue;
					Dimension d = c.getPreferredSize();
					if (rowWidth + d.width > maxWidth && rowWidth > 0) {
						width = Math.max(width, rowWidth);
						height += rowHeight + getVgap();
						rowWidth = 0;
						rowHeight = 0;
					}
					rowWidth += d.width + getHgap();
					rowHeight = Math.max(rowHeight, d.height);
				}
				width = Math.max(width, rowWidth);
				height += rowHeight + getVgap() * 2 + insets.top + insets.bottom;
				return new Dimension(width + insets.left + insets.right + getHgap() * 2, height);
			}
		}
	}
}
