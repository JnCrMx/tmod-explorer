package de.jcm.tmod.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

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

		image = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);

		byte[] bytes = in.readNBytes(width * height * 4);
		IntBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		int[] array = new int[width * height];
		buffer.get(array);

		image.getRaster().setDataElements(0, 0, width, height, array);

		in.close();
	}

	/**
	 * Create the frame.
	 */
	public RawImgViewer(File file)
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
