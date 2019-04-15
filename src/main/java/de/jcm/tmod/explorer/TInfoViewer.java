package de.jcm.tmod.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

import de.jcm.net.NetworkHelper;

public class TInfoViewer extends JFrame
{
	private static final long serialVersionUID = 1L;

	private File infoFile;

	private JPanel contentPane;
	private JTable table;

	private static final String[] columnNames = { "Key", "Value" };

	private Object[][] data = { /* 0 */ { "author", "" }, /* 1 */ { "version", "" },
			/* 2 */ { "displayName", "" }, /* 3 */ { "homepage", "" }, /* 4 */ { "noCompile", false },
			/* 5 */ { "hideCode", true }, /* 6 */ { "hideResources", true },
			/* 7 */ { "includeSource", false }, /* 8 */ { "includePDB", false },
			/* 9 */ { "editAndContinue", false }, /* 10 */ { "side", 0 },
			/* 11 */ { "beta", false } };
	private Object[][] dataCache;

	private String description;

	private final Action saveAction = new AbstractAction("Save", UIManager.getIcon("FileView.floppyDriveIcon"))
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			try
			{
				LittleEndianDataOutputStream out = new LittleEndianDataOutputStream(new FileOutputStream(infoFile));

				for(int i=0;i<4;i++)
				{
					String key = (String) data[i][1];
					String value = (String) data[i][1];

					if(!value.isBlank())
					{
						NetworkHelper.writeVarString(out, key);
						NetworkHelper.writeVarString(out, value);
					}
				}

				if((boolean)data[4][1])		//noCompile
					NetworkHelper.writeVarString(out, (String)data[4][0]);
				if(!(boolean)data[5][1])	//!hideCode
					NetworkHelper.writeVarString(out, "!"+(String)data[5][0]);
				if(!(boolean)data[6][1])	//!hideResources
					NetworkHelper.writeVarString(out, "!"+(String)data[6][0]);
				if((boolean)data[7][1])		//includeSource
					NetworkHelper.writeVarString(out, (String)data[7][0]);
				if((boolean)data[8][1])		//includePDB
					NetworkHelper.writeVarString(out, (String)data[8][0]);
				if((boolean)data[9][1])		//editAndContinue
					NetworkHelper.writeVarString(out, (String)data[9][0]);

				NetworkHelper.writeVarString(out, "side");
				out.writeByte((int)data[10][1]);

				if((boolean)data[11][1])	//beta
					NetworkHelper.writeVarString(out, (String)data[11][0]);

				out.close();
			}
			catch(IOException e1)
			{
				e1.printStackTrace();
			}
		}
	};

	private boolean isDirty(int row, Object value)
	{
		// System.out.println(row+" -> "+value+" = "+data[row][1]+" ->
		// "+value.getClass()+" = "+data[row][1].getClass());

		return !value.equals(data[row][1]);
	}

	private void read() throws IOException
	{
		LittleEndianDataInputStream in = new LittleEndianDataInputStream(new FileInputStream(infoFile));

		while(in.available() > 0)
		{
			String key = NetworkHelper.readVarString(in);

			for(int i = 0; i < 4; i++)
			{
				if(key.equals(data[i][0]))
				{
					data[i][1] = NetworkHelper.readVarString(in);
				}
			}
			switch(key)
			{
				case "noCompile":
					data[4][1] = true;
					break;
				case "!hideCode":
					data[5][1] = false;
					break;
				case "!hideResources":
					data[6][1] = false;
					break;
				case "includeSource":
					data[7][1] = true;
					break;
				case "includePDB":
					data[8][1] = true;
					break;
				case "editAndContinue":
					data[9][1] = true;
					break;
				case "side":
					data[10][1] = in.readUnsignedByte();
					break;
				case "beta":
					data[11][1] = true;
					break;
				case "description":
					description = NetworkHelper.readVarString(in);
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Create the frame.
	 */
	public TInfoViewer(File infoFile)
	{
		this.infoFile = infoFile;
		try
		{
			read();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 595, 476);
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmSave = new JMenuItem(saveAction);
		mnFile.add(mntmSave);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		dataCache = new Object[data.length][2];
		for(int i = 0; i < dataCache.length; i++)
		{
			dataCache[i][0] = data[i][0];

			if(data[i][1] instanceof String)
			{
				String string = (String) data[i][1];
				dataCache[i][1] = new String(string);
			}
			else
			{
				dataCache[i][1] = data[i][1];
			}
		}
		table = new JTable()
		{
			private static final long serialVersionUID = 1L;
			private Class<?> editingClass;

			@Override
			public TableCellRenderer getCellRenderer(int row, int column)
			{
				editingClass = null;

				int modelColumn = convertColumnIndexToModel(column);
				if(modelColumn == 1)
				{
					Class<?> rowClass = getModel().getValueAt(row, modelColumn).getClass();
					return getDefaultRenderer(rowClass);
				}
				else
				{
					return super.getCellRenderer(row, column);
				}
			}

			@Override
			public TableCellEditor getCellEditor(int row, int column)
			{
				editingClass = null;

				int modelColumn = convertColumnIndexToModel(column);
				if(modelColumn == 1)
				{
					editingClass = getModel().getValueAt(row, modelColumn).getClass();
					return getDefaultEditor(editingClass);
				}
				else
				{
					return super.getCellEditor(row, column);
				}
			}

			@Override
			public Class<?> getColumnClass(int column)
			{
				return editingClass != null ? editingClass : super.getColumnClass(column);
			}
		};

		table.setDefaultRenderer(String.class, new DefaultTableCellRenderer.UIResource()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column)
			{
				Component component =
						super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				component.setBackground(table.getBackground());
				component.setForeground(table.getForeground());
				if(column == 1)
				{
					if(isDirty(row, value))
					{
						component.setBackground(new Color(1.0f, 1.0f, 0.5f));
					}
				}
				return component;
			}
		});
		TableCellRenderer booleanRenderer = table.getDefaultRenderer(Boolean.class);
		table.setDefaultRenderer(Boolean.class, new TableCellRenderer()
		{

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column)
			{
				Component component =
						booleanRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				component.setBackground(table.getBackground());
				component.setForeground(table.getForeground());
				if(column == 1)
				{
					if(isDirty(row, value))
					{
						component.setBackground(new Color(1.0f, 1.0f, 0.5f));
					}
				}
				return component;
			}
		});
		TableCellRenderer numberRenderer = table.getDefaultRenderer(Integer.class);
		table.setDefaultRenderer(Integer.class, new TableCellRenderer()
		{

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column)
			{
				Component component =
						numberRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				component.setBackground(table.getBackground());
				component.setForeground(table.getForeground());
				if(column == 1)
				{
					if(isDirty(row, value))
					{
						component.setBackground(new Color(1.0f, 1.0f, 0.5f));
					}
				}
				return component;
			}
		});
		table.setModel(new DefaultTableModel(dataCache, columnNames)
		{
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column)
			{
				return column == 1;
			}
		});
		table.getColumnModel().getColumn(0).setPreferredWidth(94);
		contentPane.add(table, BorderLayout.CENTER);

		JPanel panel = new JPanel();
		contentPane.add(panel, BorderLayout.EAST);
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

		JLabel lblNewLabel = new JLabel("DLL references:");
		panel.add(lblNewLabel);

		JList<String> listDllReferences = new JList<>();
		panel.add(listDllReferences);

		JLabel lblModReferences = new JLabel("Mod references:");
		panel.add(lblModReferences);

		JList<String> listModReferences = new JList<>();
		panel.add(listModReferences);

		JLabel lblWeakReferences = new JLabel("Weak references:");
		panel.add(lblWeakReferences);

		JList<String> listWeakReferences = new JList<>();
		panel.add(listWeakReferences);

		JLabel lblSortAfter = new JLabel("Sort after:");
		panel.add(lblSortAfter);

		JList<String> listSortAfter = new JList<>();
		panel.add(listSortAfter);

		JLabel lblSortBefore = new JLabel("Sort before:");
		panel.add(lblSortBefore);

		JList<String> listSortBefore = new JList<>();
		panel.add(listSortBefore);

		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new TitledBorder(null, "Description", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		contentPane.add(panel_1, BorderLayout.SOUTH);
		panel_1.setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		panel_1.add(scrollPane);

		JTextArea textArea = new JTextArea();
		textArea.setRows(10);
		textArea.setText(description);
		Color bg = textArea.getBackground();
		textArea.addCaretListener(new CaretListener()
		{
			@Override
			public void caretUpdate(CaretEvent e)
			{
				if(textArea.getText().equals(description))
				{
					textArea.setBackground(bg);
				}
				else
				{
					textArea.setBackground(new Color(1.0f, 1.0f, 0.5f));
				}
			}
		});
		scrollPane.setViewportView(textArea);
	}
}
