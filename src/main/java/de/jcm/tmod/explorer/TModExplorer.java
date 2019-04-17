package de.jcm.tmod.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;

import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

import de.jcm.net.NetworkHelper;

public class TModExplorer extends JFrame
{
	private static final long serialVersionUID = 1L;

	private JPanel contentPane;

	private File file;

	private int baseAddress;

	private String tversion;
	private String modname;
	private String mversion;
	private int csize;
	private int fcount;

	public static enum TState
	{
		ORIGINAL,
		ADDED,
		CHANGED,
		REMOVED
	}

	public static class TFile
	{
		public String path;
		public int offset;
		public int size;
		public TState state;
	}

	public LinkedList<TFile> files = new LinkedList<>();
	public boolean filesMoved		= false;
	public boolean filesReOrdered	= false;

	private HashMap<TFile, File> editFiles = new HashMap<>();
	private JTextField tversionField;
	private JTextField modnameField;
	private JTextField mversionField;

	private JTree tree;

	private final Action saveAsAction = new AbstractAction("Save as...", UIManager.getIcon("FileView.floppyDriveIcon"))
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JFileChooser chooser = new JFileChooser(
					new File(System.getProperty("user.home") + "/Documents/My Games/Terraria/ModLoader/Mods"));
			FileFilter filter;
			chooser.addChoosableFileFilter(filter = new FileFilter()
			{
				@Override
				public String getDescription()
				{
					return "Terraria Mod (*.tmod)";
				}

				@Override
				public boolean accept(File f)
				{
					return f.getName().endsWith(".tmod") || f.isDirectory();
				}
			});
			int result = chooser.showSaveDialog(null);
			if(result == JFileChooser.APPROVE_OPTION)
			{
				File target = chooser.getSelectedFile();
				if(chooser.getFileFilter() == filter)
				{
					if(!target.getName().endsWith(".tmod"))
					{
						target = new File(target.getAbsolutePath() + ".tmod");
					}
				}

				try
				{
					write(target);
				}
				catch(IOException e1)
				{
					e1.printStackTrace();
				}
			}
		}
	};
	private final Action openAction = new AbstractAction("Open", UIManager.getIcon("FileView.directoryIcon"))
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			main(new String[0]);
		}
	};
	private final Action extractAllAction = new AbstractAction("Extract all...", UIManager.getIcon("FileChooser.upFolderIcon"))
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JFileChooser chooser = new JFileChooser();
			chooser.setFileFilter(new FileFilter()
			{

				@Override
				public String getDescription()
				{
					return "Directory";
				}

				@Override
				public boolean accept(File f)
				{
					return f.isDirectory();
				}
			});
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int result = chooser.showSaveDialog(null);
			if(result == JFileChooser.APPROVE_OPTION)
			{
				File root = chooser.getSelectedFile();

				for(int i = 0; i < files.size(); i++)
				{
					try
					{
						TFile file = files.get(i);
						File target = new File(root, file.path);
						target.getParentFile().mkdirs();

						BufferedInputStream in = createTFileInputStream(file);
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target));

						byte[] buffer = new byte[1024];
						for(int j = 0; j < file.size - 1023; j += 1024)
						{
							int read = in.read(buffer);
							out.write(buffer, 0, read);
						}
						buffer = new byte[file.size % 1024];
						in.read(buffer);
						out.write(buffer);

						in.close();
						out.close();
					}
					catch(IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
		}
	};
	private final Action extractFileAction = new AbstractAction("Extract file...", UIManager.getIcon("FileChooser.upFolderIcon"))
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if(tree.getSelectionPath() != null)
			{
				Stream<Object> treePath = Arrays.stream(tree.getSelectionPath().getPath());
				String path = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
				{
					if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
					{
						if(((DefaultMutableTreeNode) b).getChildCount() == 0)
							a.append(((DefaultMutableTreeNode) b).getUserObject());
						else
							a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
					}
				}, (a, b) -> a.append(b), StringBuilder::toString));
				if(!path.isBlank())
				{
					Stream<TFile> files = TModExplorer.this.files.stream();
					TFile file = files.reduce((a, b) -> b.path.equals(path) ? b : a)
							.filter(f -> f != null && f.path.equals(path)).orElse(null);
					if(file != null)
					{
						JFileChooser chooser = new JFileChooser();
						chooser.setSelectedFile(new File(file.path));
						int result = chooser.showSaveDialog(null);
						if(result == JFileChooser.APPROVE_OPTION)
						{
							try
							{
								File target = chooser.getSelectedFile();

								BufferedInputStream in = createTFileInputStream(file);
								FileOutputStream out = new FileOutputStream(target);

								byte[] buffer = new byte[1024];
								for(int j = 0; j < file.size - 1023; j += 1024)
								{
									int read = in.read(buffer);
									out.write(buffer, 0, read);
								}
								buffer = new byte[file.size % 1024];
								in.read(buffer);
								out.write(buffer);

								out.close();
								in.close();
							}
							catch(IOException e1)
							{
								e1.printStackTrace();
							}
						}
					}
				}
			}
		}
	};
	private final Action addFileAction = new AbstractAction("Add file...")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JFileChooser chooser = new JFileChooser();
			int result = chooser.showOpenDialog(null);
			if(result == JFileChooser.APPROVE_OPTION)
			{
				File file = chooser.getSelectedFile();
				if(file.isFile())
				{
					addFile(file);
				}
			}
		}
	};
	private final Action removeFileAction = new AbstractAction("Remove file")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if(tree.getSelectionPath() != null)
			{
				Stream<Object> treePath = Arrays.stream(tree.getSelectionPath().getPath());
				String path = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
				{
					if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
					{
						if(((DefaultMutableTreeNode) b).getChildCount() == 0)
							a.append(((DefaultMutableTreeNode) b).getUserObject());
						else
							a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
					}
				}, (a, b) -> a.append(b), StringBuilder::toString));
				if(!path.isBlank())
				{
					Stream<TFile> files = TModExplorer.this.files.stream();
					TFile file = files.reduce((a, b) -> b.path.equals(path) ? b : a)
							.filter(f -> f != null && f.path.equals(path)).orElse(null);
					if(file != null)
					{
						if(file.state == TState.ORIGINAL || file.state == TState.CHANGED)
						{
							file.state = TState.REMOVED;
						}
						else if(file.state == TState.ADDED)
						{
							TModExplorer.this.files.remove(file);
							rebuildTree();
						}
					}
				}
			}
		}
	};
	private final Action restoreFileAction = new AbstractAction("Restore file")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if(tree.getSelectionPath() != null)
			{
				Stream<Object> treePath = Arrays.stream(tree.getSelectionPath().getPath());
				String path = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
				{
					if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
					{
						if(((DefaultMutableTreeNode) b).getChildCount() == 0)
							a.append(((DefaultMutableTreeNode) b).getUserObject());
						else
							a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
					}
				}, (a, b) -> a.append(b), StringBuilder::toString));
				if(!path.isBlank())
				{
					Stream<TFile> files = TModExplorer.this.files.stream();
					TFile file = files.reduce((a, b) -> b.path.equals(path) ? b : a)
							.filter(f -> f != null && f.path.equals(path)).orElse(null);
					if(file != null)
					{
						if(file.state == TState.CHANGED)
						{
							file.state = TState.ORIGINAL;
							editFiles.remove(file);
						}
						else if(file.state == TState.REMOVED)
						{
							if(editFiles.containsKey(file))
							{
								if(editFiles.get(file).lastModified() > 0)
								{
									file.state = TState.CHANGED;
								}
								else
								{
									file.state = TState.ORIGINAL;
								}
							}
							else
							{
								file.state = TState.ORIGINAL;
							}
						}
					}
				}
			}
		}
	};
	private final Action importImageAction = new AbstractAction("Import image...")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			JFileChooser chooser = new JFileChooser();
			chooser.addChoosableFileFilter(new FileFilter()
			{

				@Override
				public String getDescription()
				{
					return "Supported images (*.png, *.jpg)";
				}

				@Override
				public boolean accept(File f)
				{
					return f.isDirectory() || f.getName().endsWith(".png") || f.getName().endsWith(".jpg");
				}
			});
			int result = chooser.showOpenDialog(null);
			if(result == JFileChooser.APPROVE_OPTION)
			{
				File file = chooser.getSelectedFile();
				if(file.isFile())
				{
					try
					{
						BufferedImage image1 = ImageIO.read(file);

						File target = File.createTempFile("tmod_rawimg_" + file.getName(), ".rawimg");

						int width = image1.getWidth();
						int height = image1.getHeight();

						int samplesPerPixel = 4; // This is the *4BYTE* in
													// TYPE_4BYTE_ABGR
						int[] bandOffsets = { 0, 1, 2, 3 }; // This is the order
															// (ABGR) part in
															// TYPE_4BYTE_ABGR

						DataBufferByte buffer = new DataBufferByte(width * height * 4);
						WritableRaster raster = Raster.createInterleavedRaster(buffer, width, height,
								samplesPerPixel * width, samplesPerPixel, bandOffsets, null);

						image1.copyData(raster);

						String name = file.getName();
						name = name.replace(".png", ".rawimg");
						name = name.replace(".jpg", ".rawimg");

						FileOutputStream fout = new FileOutputStream(target);
						LittleEndianDataOutputStream out = new LittleEndianDataOutputStream(fout);

						out.writeInt(1);
						out.writeInt(width);
						out.writeInt(height);

						out.write(buffer.getData());

						out.close();

						addFile(target, name);
					}
					catch(IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
		}
	};
	private final Action moveFileAction = new AbstractAction("Move file...")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if(tree.getSelectionPath() != null)
			{
				Stream<Object> treePath = Arrays.stream(tree.getSelectionPath().getPath());
				String path = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
				{
					if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
					{
						if(((DefaultMutableTreeNode) b).getChildCount() == 0)
							a.append(((DefaultMutableTreeNode) b).getUserObject());
						else
							a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
					}
				}, (a, b) -> a.append(b), StringBuilder::toString));
				if(!path.isBlank())
				{
					Stream<TFile> files = TModExplorer.this.files.stream();
					TFile file = files.reduce((a, b) -> b.path.equals(path) ? b : a)
							.filter(f -> f != null && f.path.equals(path)).orElse(null);
					if(file != null)
					{
						String newPath = JOptionPane.showInputDialog("Enter new path:", file.path);
						if(newPath!=null)
						{
							file.path = newPath;
							rebuildTree();
							filesMoved = true;
						}
					}
				}
			}
		}
	};

	private final Action reOrderFilesAction = new AbstractAction("ReOrder files...")
	{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e)
		{
			ReOrderEditor reOrderEditor = new ReOrderEditor(TModExplorer.this);
			reOrderEditor.setVisible(true);
		}
	};

	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

					JFileChooser chooser = new JFileChooser(
							new File(System.getProperty("user.home") + "/Documents/My Games/Terraria/ModLoader/Mods"));
					chooser.addChoosableFileFilter(new FileFilter()
					{
						@Override
						public String getDescription()
						{
							return "Terraria Mod (*.tmod)";
						}

						@Override
						public boolean accept(File f)
						{
							return f.getName().endsWith(".tmod") || f.isDirectory();
						}
					});
					int result = chooser.showOpenDialog(null);

					if(result == JFileChooser.APPROVE_OPTION)
					{
						TModExplorer frame = new TModExplorer(chooser.getSelectedFile());
						frame.setVisible(true);
					}
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}

	public void read() throws Exception
	{
		FileInputStream fin = new FileInputStream(file);

		LittleEndianDataInputStream in = new LittleEndianDataInputStream(fin);

		String magic = NetworkHelper.readString(in, 4);
		if(!magic.equals("TMOD"))
			throw new IllegalArgumentException("not a TMOD file");

		int vLen = in.readUnsignedByte();
		tversion = NetworkHelper.readString(in, vLen);

		in.skipBytes(20); // Hash
		in.skipBytes(256); // Signature

		csize = in.readInt();

		baseAddress = 4 + 1 + vLen + 20 + 256 + 4;

		in = new LittleEndianDataInputStream(new BufferedInputStream(new InflaterInputStream(fin, new Inflater(true))));
		int pos = 0;

		int nLen = in.readUnsignedByte();
		modname = NetworkHelper.readString(in, nLen);
		pos += 1 + nLen;

		vLen = in.readUnsignedByte();
		mversion = NetworkHelper.readString(in, vLen);
		pos += 1 + vLen;

		fcount = in.readInt();
		pos += 4;

		files.clear();
		for(int i = 0; i < fcount; i++)
		{
			TFile file = new TFile();

			int fnLen = in.readUnsignedByte();
			file.path = NetworkHelper.readString(in, fnLen);
			pos += 1 + fnLen;

			file.size = in.readInt();
			pos += 4;
			file.offset = pos;

			int toSkip = file.size;
			while(toSkip > 0)
			{
				toSkip -= in.skip(toSkip);
			}
			pos += file.size;

			// System.out.println(file.path + " " + file.size + " @ " +
			// file.offset);
			file.state = TState.ORIGINAL;
			files.add(file);
		}
	}

	public void write(File target) throws IOException
	{
		FileOutputStream fout = new FileOutputStream(target);
		LittleEndianDataOutputStream out2 = new LittleEndianDataOutputStream(fout);

		out2.write("TMOD".getBytes());

		out2.writeByte(tversionField.getText().getBytes().length);
		out2.write(tversionField.getText().getBytes());

		out2.write(new byte[20]); // Hash
		out2.write(new byte[256]); // Signature

		out2.writeInt(0); // Size (set later!!!!!)

		DeflaterOutputStream dout = new DeflaterOutputStream(fout, new Deflater(9, true));
		LittleEndianDataOutputStream out = new LittleEndianDataOutputStream(dout);

		out.writeByte(modnameField.getText().getBytes().length);
		out.write(modnameField.getText().getBytes());

		out.writeByte(mversionField.getText().getBytes().length);
		out.write(mversionField.getText().getBytes());

		out.writeInt(files.size());

		for(int i = 0; i < files.size(); i++)
		{
			TFile file = files.get(i);

			out.writeByte(file.path.getBytes().length);
			out.write(file.path.getBytes());

			if(file.state == TState.ADDED || file.state == TState.CHANGED)
			{
				File editFile = editFiles.get(file);

				int size = (int) editFile.length();
				out.writeInt(size);

				BufferedInputStream in = new BufferedInputStream(new FileInputStream(editFile));

				byte[] buffer = new byte[1024];
				for(int j = 0; j < size - 1023; j += 1024)
				{
					int read = in.read(buffer);
					out.write(buffer, 0, read);
				}
				buffer = new byte[size % 1024];
				in.read(buffer);
				out.write(buffer);

				in.close();
			}
			else if(file.state == TState.ORIGINAL)
			{
				out.writeInt(file.size);

				BufferedInputStream in = createTFileInputStream(file);
				byte[] buffer = new byte[1024];
				for(int j = 0; j < file.size - 1023; j += 1024)
				{
					int read = in.read(buffer);
					out.write(buffer, 0, read);
				}
				buffer = new byte[file.size % 1024];
				in.read(buffer);
				out.write(buffer);

				in.close();
			}
		}

		out.close();
		out2.close();

		int sizeOffset = 4 + 1 + tversion.getBytes().length + 20 + 256;
		int headerSize = sizeOffset + 4;

		FileChannel channel = FileChannel.open(target.toPath(), StandardOpenOption.SYNC, StandardOpenOption.WRITE,
				StandardOpenOption.READ);
		ByteBuffer buffer = channel.map(MapMode.READ_WRITE, sizeOffset, 4).order(ByteOrder.LITTLE_ENDIAN);

		buffer.putInt((int) (target.length() - headerSize));

		channel.close();
	}

	public void rebuildTree()
	{
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(modname);
		files.forEach(f -> addPath(root, f.path));

		DefaultTreeModel model = new DefaultTreeModel(root);
		tree.setModel(model);

		tree.revalidate();
	}

	public void addPath(DefaultMutableTreeNode node, String path)
	{
		if(!path.contains("/"))
		{
			node.add(new DefaultMutableTreeNode(path));
		}
		else
		{
			int sep = path.indexOf('/');
			String part1 = path.substring(0, sep);
			String part2 = path.substring(sep + 1);

			Enumeration<TreeNode> childs = node.children();
			while(childs.hasMoreElements())
			{
				DefaultMutableTreeNode subnode = (DefaultMutableTreeNode) childs.nextElement();
				if(subnode.getUserObject().equals(part1))
				{
					addPath(subnode, part2);
					return;
				}
			}

			DefaultMutableTreeNode subnode = new DefaultMutableTreeNode(part1);
			node.add(subnode);
			addPath(subnode, part2);
		}
	}

	public BufferedInputStream createTFileInputStream(TFile file) throws IOException
	{
		FileInputStream fin = new FileInputStream(TModExplorer.this.file);
		fin.skip(baseAddress);

		BufferedInputStream in = new BufferedInputStream(new InflaterInputStream(fin, new Inflater(true)));
		in.skip(file.offset);

		return in;
	}

	public void addFile(File file, String name)
	{
		String path = name;
		if(tree.getSelectionPath() != null)
		{
			Stream<Object> treePath = Arrays.stream(tree.getSelectionPath().getPath());
			String parent = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
			{
				if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
				{
					if(((DefaultMutableTreeNode) b).getChildCount() == 0)
						a.append(((DefaultMutableTreeNode) b).getUserObject());
					else
						a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
				}
			}, (a, b) -> a.append(b), StringBuilder::toString));

			if(parent.contains("/"))
			{
				path = parent.substring(0, parent.lastIndexOf("/") + 1) + path;
			}
		}

		TFile tf = new TFile();
		tf.size = (int) file.length();
		tf.path = path;
		tf.state = TState.ADDED;

		files.add(tf);
		editFiles.put(tf, file);

		rebuildTree();
	}

	public void addFile(File file)
	{
		addFile(file, file.getName());
	}

	/**
	 * Create the frame.
	 */
	public TModExplorer(File file)
	{
		this.file = file;
		try
		{
			read();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 450, 300);

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmOpen = new JMenuItem(openAction);
		mnFile.add(mntmOpen);

		JMenuItem mntmSaveAs = new JMenuItem(saveAsAction);
		mnFile.add(mntmSaveAs);

		JMenuItem mntmExtract = new JMenuItem(extractAllAction);
		mnFile.add(mntmExtract);

		JMenuItem mntmExtractFile = new JMenuItem(extractFileAction);
		mnFile.add(mntmExtractFile);

		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);

		JMenuItem mntmAddFile = new JMenuItem(addFileAction);
		mnEdit.add(mntmAddFile);

		JMenuItem mntmRemoveFile = new JMenuItem(removeFileAction);
		mnEdit.add(mntmRemoveFile);

		JMenuItem mntmRestoreFile = new JMenuItem(restoreFileAction);
		mnEdit.add(mntmRestoreFile);

		JMenuItem mntmMoveFile = new JMenuItem(moveFileAction);
		mnEdit.add(mntmMoveFile);

		JMenu mnTools = new JMenu("Tools");
		menuBar.add(mnTools);

		JMenuItem mntmImportImage = new JMenuItem(importImageAction);
		mnTools.add(mntmImportImage);

		JMenuItem mntmReorderFiles = new JMenuItem(reOrderFilesAction);
		mnTools.add(mntmReorderFiles);

		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);

		contentPane.setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		contentPane.add(panel, BorderLayout.EAST);

		JPanel panel_4 = new JPanel();
		panel_4.setLayout(new BoxLayout(panel_4, BoxLayout.PAGE_AXIS));

		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new TitledBorder(null, "TMOD-Version", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_4.add(panel_1);
		panel_1.setLayout(new FlowLayout(FlowLayout.LEADING, 5, 5));

		tversionField = new JTextField();
		Color bg = tversionField.getBackground();
		tversionField.addCaretListener(new CaretListener()
		{
			public void caretUpdate(CaretEvent e)
			{
				if(tversionField.getText().equals(tversion))
				{
					tversionField.setBackground(bg);
				}
				else
				{
					tversionField.setBackground(new Color(1.0f, 1.0f, 0.5f));
				}
			}
		});
		panel_1.add(tversionField);
		tversionField.setColumns(10);
		tversionField.setText(tversion);

		JPanel panel_2 = new JPanel();
		panel_2.setBorder(new TitledBorder(null, "Mod-Name", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_4.add(panel_2);
		panel_2.setLayout(new FlowLayout(FlowLayout.LEADING, 5, 5));

		modnameField = new JTextField();
		panel_2.add(modnameField);
		modnameField.setColumns(10);
		modnameField.setText(modname);
		modnameField.addCaretListener(new CaretListener()
		{
			public void caretUpdate(CaretEvent e)
			{
				if(modnameField.getText().equals(modname))
				{
					modnameField.setBackground(bg);
				}
				else
				{
					modnameField.setBackground(new Color(1.0f, 1.0f, 0.5f));
				}
			}
		});

		JPanel panel_3 = new JPanel();
		panel_3.setBorder(new TitledBorder(null, "Mod-Version", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_4.add(panel_3);
		panel_3.setLayout(new FlowLayout(FlowLayout.LEADING, 5, 5));

		mversionField = new JTextField();
		panel_3.add(mversionField);
		mversionField.setColumns(10);
		mversionField.setText(mversion);
		mversionField.addCaretListener(new CaretListener()
		{
			public void caretUpdate(CaretEvent e)
			{
				if(mversionField.getText().equals(mversion))
				{
					mversionField.setBackground(bg);
				}
				else
				{
					mversionField.setBackground(new Color(1.0f, 1.0f, 0.5f));
				}
			}
		});

		JLabel lblCompressedSize = new JLabel("Compressed size: " + csize);

		JLabel lblFileCount = new JLabel("File count: " + fcount);
		GroupLayout gl_panel = new GroupLayout(panel);
		gl_panel.setHorizontalGroup(gl_panel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_panel.createSequentialGroup().addGap(5).addComponent(panel_4, GroupLayout.DEFAULT_SIZE,
						GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
				.addGroup(gl_panel.createSequentialGroup().addGap(5).addComponent(lblCompressedSize))
				.addGroup(gl_panel.createSequentialGroup().addGap(5).addComponent(lblFileCount)));
		gl_panel.setVerticalGroup(gl_panel.createParallelGroup(Alignment.LEADING).addGroup(gl_panel
				.createSequentialGroup().addGap(5)
				.addComponent(panel_4, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
				.addGap(5).addComponent(lblCompressedSize).addGap(5).addComponent(lblFileCount)));
		panel.setLayout(gl_panel);

		JScrollPane scrollPane = new JScrollPane();
		contentPane.add(scrollPane);

		tree = new JTree();
		scrollPane.setViewportView(tree);
		rebuildTree();
		TreeCellRenderer oldRenderer = tree.getCellRenderer();
		tree.setCellRenderer(new TreeCellRenderer()
		{

			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
					boolean leaf, int row, boolean hasFocus)
			{
				DefaultTreeCellRenderer component = (DefaultTreeCellRenderer) oldRenderer
						.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

				component.setBackgroundNonSelectionColor(tree.getBackground());
				if(tree.getPathForRow(row) != null)
				{
					Stream<Object> treePath = Arrays.stream(tree.getPathForRow(row).getPath());
					String path = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
					{
						if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
						{
							if(((DefaultMutableTreeNode) b).getChildCount() == 0)
								a.append(((DefaultMutableTreeNode) b).getUserObject());
							else
								a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
						}
					}, (a, b) -> a.append(b), StringBuilder::toString));
					if(!path.isBlank())
					{
						Stream<TFile> files = TModExplorer.this.files.stream();
						TFile file = files.reduce((a, b) -> b.path.equals(path) ? b : a)
								.filter(f -> f != null && f.path.equals(path)).orElse(null);
						if(file != null)
						{
							switch(file.state)
							{
								case ADDED:
									component.setBackgroundNonSelectionColor(new Color(0.5f, 1.0f, 0.5f));
									break;
								case CHANGED:
									component.setBackgroundNonSelectionColor(new Color(1.0f, 1.0f, 0.5f));
									break;
								case REMOVED:
									component.setBackgroundNonSelectionColor(new Color(1.0f, 0.5f, 0.5f));
									break;
								default:
									break;
							}
						}
					}
				}

				return component;
			}
		});
		tree.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if(e.getButton() == MouseEvent.BUTTON1)
				{
					if(e.getClickCount() == 2)
					{
						if(tree.getSelectionPath() != null)
						{
							Stream<Object> treePath = Arrays.stream(tree.getSelectionPath().getPath());
							String path = treePath.collect(Collector.of(() -> new StringBuilder(), (a, b) ->
							{
								if(!((DefaultMutableTreeNode) b).getUserObject().equals(modname))
								{
									if(((DefaultMutableTreeNode) b).getChildCount() == 0)
										a.append(((DefaultMutableTreeNode) b).getUserObject());
									else
										a.append(((DefaultMutableTreeNode) b).getUserObject() + "/");
								}
							}, (a, b) -> a.append(b), StringBuilder::toString));
							if(!path.isBlank())
							{
								Stream<TFile> files = TModExplorer.this.files.stream();
								TFile file = files.reduce((a, b) -> b.path.equals(path) ? b : a)
										.filter(f -> f != null && f.path.equals(path)).orElse(null);
								if(file != null)
								{
									try
									{
										File target;

										int index = file.path.lastIndexOf('.');
										String name;
										String prefix;
										String suffix;
										if(index == -1)
										{
											name = file.path;
											prefix = "tmod_" + modname + name.replace('/', '_');
											suffix = "";
										}
										else
										{
											name = file.path.substring(0, index);
											prefix = "tmod_" + modname + name.replace('/', '_');
											suffix = file.path.substring(file.path.lastIndexOf('.'));
										}

										if(editFiles.containsKey(file))
										{
											target = editFiles.get(file);
										}
										else
										{
											BufferedInputStream in = createTFileInputStream(file);

											target = File.createTempFile(prefix, suffix);
											target.deleteOnExit();
											FileOutputStream out = new FileOutputStream(target);

											byte[] buffer = new byte[1024];
											for(int j = 0; j < file.size - 1023; j += 1024)
											{
												int read = in.read(buffer);
												out.write(buffer, 0, read);
											}
											buffer = new byte[file.size % 1024];
											in.read(buffer);
											out.write(buffer);

											out.close();
											in.close();

											target.setLastModified(0);
											editFiles.put(file, target);
										}

										switch(suffix)
										{
											case ".rawimg":
												RawImgViewer rawImgViewer = new RawImgViewer(target, file.path);
												rawImgViewer.setVisible(true);
												break;
											case ".dll":

												break;
											case "":
												switch(name)
												{
													case "Info":
														TInfoViewer tInfoViewer = new TInfoViewer(target);
														tInfoViewer.setVisible(true);
														break;
													default:
														Desktop.getDesktop().open(target);
														break;
												}
												break;
											default:
												Desktop.getDesktop().open(target);
												break;
										}
									}
									catch(IOException e1)
									{
										e1.printStackTrace();
									}
								}
							}
						}
					}
				}
			}
		});
		addWindowFocusListener(new WindowFocusListener()
		{

			@Override
			public void windowLostFocus(WindowEvent e)
			{

			}

			@Override
			public void windowGainedFocus(WindowEvent e)
			{
				editFiles.forEach((tfile, file) ->
				{
					if(file.lastModified() > 0)
					{
						if(tfile.state == TState.ORIGINAL)
						{
							tfile.state = TState.CHANGED;
							System.out.println("File " + tfile.path + " at " + file.getAbsolutePath() + " changed!");
						}
					}
				});
			}
		});
	}

}
