package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.betacraft.launcher.InstanceSettings.OptionsPanel;

/** Gallery of built-in pixel-art icons plus "your own picture" */
public class IconPicker extends JDialog {
	public static final String[] BUILT_IN = {
		"zloy", "grass", "dirt", "stone", "cobblestone", "planks", "log", "sand",
		"gravel", "bricks", "bookshelf", "tnt", "diamond_ore", "gold_ore", "redstone_ore", "diamond_block",
		"gold_block", "obsidian", "netherrack", "pumpkin", "creeper", "steve", "herobrine"
	};

	private boolean changed = false;
	/** Where the chosen icon is stored as a PNG; no file means the default icon */
	private final File iconFile;

	/** Lets the user pick an icon for the instance, returns true if it changed */
	public static boolean pick(Component parent, Instance instance) {
		return pick(parent, instance.name, instance.getIconFile(), null);
	}

	/**
	 * Picks an icon for anything that keeps it in one PNG file (instances, saved servers).
	 * @param betaCraftAddress a server address to offer its icon from BetaCraft's list, or null
	 */
	public static boolean pick(Component parent, String name, File iconFile, String betaCraftAddress) {
		IconPicker p = new IconPicker(parent == null ? null : SwingUtilities.getWindowAncestor(parent), name, iconFile, betaCraftAddress);
		p.setVisible(true);
		return p.changed;
	}

	private void setIcon(java.io.InputStream in) {
		iconFile.getParentFile().mkdirs();
		Util.copy(in, iconFile);
	}

	private IconPicker(java.awt.Window owner, String targetName, File iconFile, final String betaCraftAddress) {
		super(owner, String.format(Lang.ICON_PICKER_TITLE, targetName), ModalityType.APPLICATION_MODAL);
		this.iconFile = iconFile;
		this.setIconImage(Window.img);
		this.setLayout(new BorderLayout());

		JPanel grid = new OptionsPanel();
		grid.setLayout(new GridLayout(0, 8, 6, 6));
		grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		ButtonGroup group = new ButtonGroup();

		for (final String name : BUILT_IN) {
			try {
				BufferedImage img = ImageIO.read(IconPicker.class.getResourceAsStream("/icons/instance/" + name + ".png"));
				JToggleButton b = new JToggleButton(new ImageIcon(Instance.scaleIcon(img, 48)));
				b.setToolTipText(name);
				b.setPreferredSize(new Dimension(60, 60));
				b.setBackground(Color.DARK_GRAY);
				b.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						setIcon(IconPicker.class.getResourceAsStream("/icons/instance/" + name + ".png"));
						changed = true;
						dispose();
					}
				});
				group.add(b);
				grid.add(b);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton own = new JButton(Lang.ICON_PICKER_OWN);
		own.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFile();
			}
		});
		bottom.add(own);

		if (betaCraftAddress != null) {
			JButton fromList = new JButton(Lang.ICON_PICKER_BETACRAFT);
			fromList.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					byte[] data = ServerStatus.betaCraftIcon(betaCraftAddress);
					if (data == null) {
						JOptionPane.showMessageDialog(IconPicker.this, Lang.ICON_PICKER_BETACRAFT_NONE, getTitle(), JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					setIcon(new java.io.ByteArrayInputStream(data));
					changed = true;
					dispose();
				}
			});
			bottom.add(fromList);
		}

		JButton reset = new JButton(Lang.ICON_PICKER_RESET);
		reset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				IconPicker.this.iconFile.delete();
				changed = true;
				dispose();
			}
		});
		bottom.add(reset);

		JButton cancel = new JButton(Lang.CANCEL);
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		bottom.add(cancel);

		this.add(grid, BorderLayout.CENTER);
		this.add(bottom, BorderLayout.SOUTH);
		this.pack();
		this.setResizable(false);
		this.setLocationRelativeTo(owner);
	}

	private void chooseFile() {
		JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
		chooser.setDialogTitle(Lang.INSTANCE_CHANGE_ICON_TITLE);
		chooser.setFileFilter(new FileNameExtensionFilter("PNG, JPG, GIF, BMP", "png", "jpg", "jpeg", "gif", "bmp"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		try {
			BufferedImage img = ImageIO.read(chooser.getSelectedFile());
			if (img == null) {
				JOptionPane.showMessageDialog(this, String.format(Lang.INSTANCE_CHANGE_ICON_UNSUPPORTED, "PNG, JPG, GIF, BMP"), Lang.INSTANCE_CHANGE_ICON_UNSUPPORTED_TITLE, JOptionPane.WARNING_MESSAGE);
				return;
			}
			// stored as a square PNG, big pictures are shrunk so the list stays fast
			int size = Math.min(256, Math.max(img.getWidth(), img.getHeight()));
			iconFile.getParentFile().mkdirs();
			ImageIO.write(Instance.scaleIcon(img, size), "png", iconFile);
			changed = true;
			dispose();
		} catch (Throwable t) {
			t.printStackTrace();
			JOptionPane.showMessageDialog(this, String.format(Lang.INSTANCE_CHANGE_ICON_FAILED, t.getMessage()), Lang.INSTANCE_CHANGE_ICON_FAILED_TITLE, JOptionPane.ERROR_MESSAGE);
		}
	}
}
