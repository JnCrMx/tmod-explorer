package de.jcm.tmod.explorer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;

import com.google.common.io.LittleEndianDataInputStream;

public class RawImgViewer extends JFrame
{
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;

	private File file;
	private BufferedImage image;

	private void read() throws IOException
	{
		LittleEndianDataInputStream in = new LittleEndianDataInputStream(new FileInputStream(file));

		int version = in.readInt();
		if(version != 1)
		{
			image = new BufferedImage(0, 0, BufferedImage.TYPE_INT_ARGB);
			in.close();
			return;
		}

		int width = in.readInt();
		int height = in.readInt();

		byte[] bytes = in.readNBytes(width * height * 4);

	    int samplesPerPixel = 4; // This is the *4BYTE* in TYPE_4BYTE_ABGR
	    int[] bandOffsets = {0, 1, 2, 3}; // This is the order (ABGR) part in TYPE_4BYTE_ABGR

		DataBuffer data = new DataBufferByte(bytes, bytes.length);
		WritableRaster raster = Raster.createInterleavedRaster(data, width, height, samplesPerPixel * width, samplesPerPixel, bandOffsets, null);

		ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), true, false,
				Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
	    image = new BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied(), null);

		in.close();
	}

	/**
	 * Create the frame.
	 */
	public RawImgViewer(File file, String path)
	{
		this.file = file;
		try
		{
			read();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 450, 300);

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenu mnExportAs = new JMenu("Export as...");
		mnFile.add(mnExportAs);

		JMenuItem mntmPng = new JMenuItem("PNG");
		mntmPng.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JFileChooser chooser = new JFileChooser();
				chooser.setSelectedFile(new File(path.replace(".rawimg", ".png")));
				FileFilter filter;
				chooser.addChoosableFileFilter(filter = new FileFilter()
				{
					@Override
					public String getDescription()
					{
						return "PNG-Image (*.png)";
					}

					@Override
					public boolean accept(File f)
					{
						return f.isDirectory() || f.getName().endsWith(".png");
					}
				});
				int result = chooser.showSaveDialog(null);
				if(result == JFileChooser.APPROVE_OPTION)
				{
					File target = chooser.getSelectedFile();
					if(chooser.getFileFilter() == filter)
					{
						if(!target.getName().endsWith(".png"))
						{
							target = new File(target.getAbsolutePath() + ".png");
						}
					}
					try
					{
						ImageIO.write(image, "png", target);
					}
					catch(IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
		});
		mnExportAs.add(mntmPng);

		JMenuItem mntmJpeg = new JMenuItem("JPEG");
		mntmJpeg.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JFileChooser chooser = new JFileChooser();
				chooser.setSelectedFile(new File(path.replace(".rawimg", ".jpg")));
				FileFilter filter;
				chooser.addChoosableFileFilter(filter = new FileFilter()
				{
					@Override
					public String getDescription()
					{
						return "JPEG-Image (*.jpg)";
					}

					@Override
					public boolean accept(File f)
					{
						return f.isDirectory() || f.getName().endsWith(".jpg");
					}
				});
				int result = chooser.showSaveDialog(null);
				if(result == JFileChooser.APPROVE_OPTION)
				{
					File target = chooser.getSelectedFile();
					if(chooser.getFileFilter() == filter)
					{
						if(!target.getName().endsWith(".jpg"))
						{
							target = new File(target.getAbsolutePath() + ".jpg");
						}
					}
					try
					{
						ImageIO.write(image, "jpg", target);
					}
					catch(IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
		});
		mnExportAs.add(mntmJpeg);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		JPanel canvas = new JPanel()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void paintComponent(Graphics g)
			{
				g.drawImage(image, 1, 1, null);
				g.drawRect(0, 0, image.getWidth() + 1, image.getHeight() + 1);
			}
		};
		canvas.setPreferredSize(new Dimension(image.getWidth() + 2, image.getHeight() + 2));
		contentPane.add(canvas, BorderLayout.CENTER);

		pack();
	}

}
