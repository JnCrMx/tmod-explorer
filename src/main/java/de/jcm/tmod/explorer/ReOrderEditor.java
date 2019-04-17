package de.jcm.tmod.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import de.jcm.tmod.explorer.TModExplorer.TFile;

import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

public class ReOrderEditor extends JFrame
{
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;

	private final LinkedList<TFile> fileList;
	private String[] filePaths;
	private final JList<String> list;

	private final TModExplorer explorer;
	private boolean changed = false;

	private final Color listBackground;

	private final Action up1Action = new AbstractAction("1 Up")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int index = list.getSelectedIndex();
			if(index>0)
			{
				TFile file = fileList.remove(index);
				fileList.add(index-1, file);

				rebuildList();
				list.setSelectedIndex(index-1);
			}
		}
	};

	private final Action up10Action = new AbstractAction("10 Up")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int index = list.getSelectedIndex();
			if(index>0)
			{
				TFile file = fileList.remove(index);
				fileList.add(Math.max(index-10, 0), file);
				rebuildList();
				list.setSelectedIndex(Math.max(index-10, 0));
			}
		}
	};

	private final Action topAction = new AbstractAction("Top")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int index = list.getSelectedIndex();
			if(index>0)
			{
				TFile file = fileList.remove(index);
				fileList.addFirst(file);

				rebuildList();
				list.setSelectedIndex(0);
			}
		}
	};

	private final Action down1Action = new AbstractAction("1 Down")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int index = list.getSelectedIndex();
			if(index<fileList.size()-1)
			{
				TFile file = fileList.remove(index);
				fileList.add(index+1, file);

				rebuildList();
				list.setSelectedIndex(index+1);
			}
		}
	};

	private final Action down10Action = new AbstractAction("10 Down")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int index = list.getSelectedIndex();
			if(index<fileList.size()-1)
			{
				TFile file = fileList.remove(index);
				int newIndex = Math.min(index+10, fileList.size());
				fileList.add(newIndex, file);
				rebuildList();
				list.setSelectedIndex(newIndex);
			}
		}
	};

	private final Action bottomAction = new AbstractAction("Bottom")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			int index = list.getSelectedIndex();
			if(index<fileList.size()-1)
			{
				TFile file = fileList.remove(index);
				fileList.addLast(file);

				rebuildList();
				list.setSelectedIndex(fileList.size()-1);
			}
		}
	};

	private final Action saveAction = new AbstractAction("Save", UIManager.getIcon("FileView.floppyDriveIcon"))
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			explorer.files = fileList;
			explorer.filesReOrdered = true;

			rebuildList();
			explorer.rebuildTree();
		}
	};

	public void rebuildList()
	{
		filePaths = fileList.stream().flatMap(file -> Stream.of(file.path)).toArray(String[]::new);
		list.setListData(filePaths);
		list.revalidate();

		for(int i=0;i<explorer.files.size();i++)
		{
			TFile file1 = fileList.get(i);
			TFile file2 = explorer.files.get(i);

			changed = false;
			if(file1!=file2)
			{
				changed = true;
				break;
			}
		}
		if(changed)
		{
			list.setBackground(new Color(1.0f, 1.0f, 0.5f));
		}
		else
		{
			list.setBackground(listBackground);
		}
	}
	/**
	 * Create the frame.
	 */

	@SuppressWarnings("unchecked")
	public ReOrderEditor(TModExplorer explorer)
	{
		this.explorer = explorer;
		fileList = (LinkedList<TFile>) explorer.files.clone();

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setBounds(100, 100, 450, 300);

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

		JPanel panel = new JPanel();
		contentPane.add(panel, BorderLayout.NORTH);
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JButton btnTop = new JButton(topAction);
		panel.add(btnTop);

		JButton btnUp_1 = new JButton(up10Action);
		panel.add(btnUp_1);

		JButton btnUp = new JButton(up1Action);
		panel.add(btnUp);

		JButton btnDown = new JButton(down1Action);
		panel.add(btnDown);

		JButton btnDown_1 = new JButton(down10Action);
		panel.add(btnDown_1);

		JButton btnBottom = new JButton(bottomAction);
		panel.add(btnBottom);

		JScrollPane scrollPane = new JScrollPane();
		contentPane.add(scrollPane, BorderLayout.CENTER);

		list = new JList<String>();
		listBackground = list.getBackground();
		rebuildList();
		scrollPane.setViewportView(list);
	}

}
