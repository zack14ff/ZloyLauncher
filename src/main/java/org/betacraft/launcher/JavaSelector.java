package org.betacraft.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.betacraft.launcher.InstanceSettings.OptionsPanel;
import org.betacraft.launcher.JavaFinder.JavaInstall;

/** Java picker in the spirit of Prism Launcher: every Java found on the computer, with version and architecture */
public class JavaSelector extends JDialog {
	public static final Color RECOMMENDED = new Color(0x1E7A1E);
	public static final Color TOO_NEW = new Color(0xB06000);

	private final List<JavaInstall> rows = new ArrayList<JavaInstall>();
	private final JavaTableModel model = new JavaTableModel();
	private final JTable table;
	private final JLabel status;
	private final JButton refreshButton, selectButton;
	private final String current;
	private String result = null;

	/** Shows the picker, returns the chosen java executable or null */
	public static String choose(Component parent, String current) {
		java.awt.Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
		if (parent instanceof java.awt.Window) owner = (java.awt.Window) parent;
		JavaSelector s = new JavaSelector(owner, current);
		s.setVisible(true);
		return s.result;
	}

	private JavaSelector(java.awt.Window owner, String current) {
		super(owner, Lang.JAVA_SELECT_TITLE, ModalityType.APPLICATION_MODAL);
		this.current = current;
		this.setIconImage(Window.img);
		this.setLayout(new BorderLayout());

		table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(22);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setDefaultRenderer(Object.class, new JavaRenderer());
		table.getColumnModel().getColumn(0).setPreferredWidth(90);
		table.getColumnModel().getColumn(1).setPreferredWidth(70);
		table.getColumnModel().getColumn(2).setPreferredWidth(150);
		table.getColumnModel().getColumn(3).setPreferredWidth(420);
		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) selectChosen();
			}
		});

		JScrollPane scroller = new JScrollPane(table);
		scroller.setPreferredSize(new Dimension(760, 280));

		JPanel center = new OptionsPanel();
		center.setLayout(new BorderLayout(0, 6));
		center.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
		JLabel hint = new JLabel(Lang.JAVA_SELECT_HINT);
		hint.setForeground(Color.LIGHT_GRAY);
		center.add(hint, BorderLayout.NORTH);
		center.add(scroller, BorderLayout.CENTER);
		status = new JLabel(" ");
		status.setForeground(Color.LIGHT_GRAY);
		center.add(status, BorderLayout.SOUTH);

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
		refreshButton = new JButton(Lang.JAVA_SELECT_REFRESH);
		refreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				load(true);
			}
		});
		left.add(refreshButton);

		JButton browse = new JButton(Lang.BROWSE);
		browse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				browse();
			}
		});
		left.add(browse);

		JButton download = new JButton(Lang.JAVA_SELECT_DOWNLOAD);
		download.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String os = OS.isWindows() ? "windows" : OS.isMac() ? "mac" : "linux";
				Util.openURL("https://adoptium.net/temurin/releases/?version=8&package=jre&arch=x64&os=" + os);
			}
		});
		left.add(download);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		selectButton = new JButton(Lang.SELECT);
		selectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selectChosen();
			}
		});
		right.add(selectButton);
		JButton cancel = new JButton(Lang.CANCEL);
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		right.add(cancel);

		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(left, BorderLayout.WEST);
		bottom.add(right, BorderLayout.EAST);

		this.add(center, BorderLayout.CENTER);
		this.add(bottom, BorderLayout.SOUTH);
		this.pack();
		this.setLocationRelativeTo(owner);
		load(false);
	}

	private void load(final boolean rescan) {
		refreshButton.setEnabled(false);
		status.setText(Lang.JAVA_SELECT_SEARCHING);
		new Thread("Java-Scan") {
			public void run() {
				final List<JavaInstall> found = rescan ? JavaFinder.rescan() : JavaFinder.getInstalls();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						rows.clear();
						rows.addAll(found);
						// a manually set Java that wasn't found by the scan still belongs in the list
						if (current != null && current.length() > 0 && indexOf(current) == -1) {
							JavaInstall manual = JavaFinder.probe(current);
							if (manual != null) rows.add(0, manual);
						}
						model.fireTableDataChanged();
						int sel = current == null ? -1 : indexOf(current);
						if (sel == -1 && !rows.isEmpty()) sel = 0;
						if (sel != -1) table.setRowSelectionInterval(sel, sel);
						status.setText(rows.isEmpty() ? Lang.JAVA_SELECT_NONE : String.format(Lang.JAVA_SELECT_FOUND, rows.size()));
						refreshButton.setEnabled(true);
					}
				});
			}
		}.start();
	}

	private int indexOf(String path) {
		for (int i = 0; i < rows.size(); i++) {
			if (JavaFinder.sameInstall(rows.get(i), path)) return i;
		}
		return -1;
	}

	private void browse() {
		JFileChooser chooser = new JFileChooser();
		if (current != null) chooser.setCurrentDirectory(new File(current).getParentFile());
		chooser.setDialogTitle(Lang.JAVA_EXECUTABLE);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		File f = chooser.getSelectedFile();
		if (f.getAbsolutePath().endsWith(".plugin") && OS.isMac()) f = new File(f, "Contents/Home/bin/java");
		// picking javaw.exe is a common mistake: the console-less one can't report its version
		if (f.getName().equalsIgnoreCase("javaw.exe")) f = new File(f.getParentFile(), "java.exe");

		JavaInstall j = JavaFinder.probe(f.getAbsolutePath());
		if (j == null) {
			JOptionPane.showMessageDialog(this, Lang.JAVA_INVALID_FILE, Lang.JAVA_SELECT_TITLE, JOptionPane.WARNING_MESSAGE);
			return;
		}
		int i = indexOf(j.path);
		if (i == -1) {
			rows.add(0, j);
			model.fireTableDataChanged();
			i = 0;
		}
		table.setRowSelectionInterval(i, i);
	}

	private void selectChosen() {
		int row = table.getSelectedRow();
		if (row < 0) {
			JOptionPane.showMessageDialog(this, Lang.ERR_NO_SELECTION);
			return;
		}
		result = rows.get(row).path;
		dispose();
	}

	public static String describe(JavaInstall j) {
		if (j.isRecommended()) return Lang.JAVA_SELECT_RECOMMENDED;
		if (j.major > 8) return Lang.JAVA_SELECT_TOO_NEW;
		if (j.major == 8) return Lang.JAVA_SELECT_32BIT;
		return Lang.JAVA_SELECT_TOO_OLD;
	}

	class JavaTableModel extends AbstractTableModel {
		public int getRowCount() {
			return rows.size();
		}

		public int getColumnCount() {
			return 4;
		}

		public String getColumnName(int column) {
			switch (column) {
				case 0: return Lang.JAVA_COLUMN_VERSION;
				case 1: return Lang.JAVA_COLUMN_ARCH;
				case 2: return Lang.JAVA_COLUMN_VENDOR;
				default: return Lang.JAVA_COLUMN_PATH;
			}
		}

		public Object getValueAt(int row, int column) {
			JavaInstall j = rows.get(row);
			switch (column) {
				case 0: return j.version;
				case 1: return j.arch;
				case 2: return j.vendor;
				default: return j.path;
			}
		}
	}

	class JavaRenderer extends DefaultTableCellRenderer {
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			JavaInstall j = rows.get(row);
			l.setToolTipText("<html>" + j.path + "<br>" + describe(j) + "</html>");
			l.setFont(l.getFont().deriveFont(j.isRecommended() ? Font.BOLD : Font.PLAIN));
			if (!isSelected) {
				if (j.isRecommended()) l.setForeground(RECOMMENDED);
				else if (j.major > 8) l.setForeground(TOO_NEW);
				else l.setForeground(Color.DARK_GRAY);
			}
			return l;
		}
	}
}
