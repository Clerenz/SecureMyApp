package de.clemensloos.securemyapp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.sun.awt.AWTUtilities;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;

public class SecureGui {

	static HHOOK hhk;
	private static LowLevelKeyboardProc keyboardHook;
	final static User32 lib = User32.INSTANCE;
	
	// passphrase
	static int[] passw = {69, 88, 73, 84};
	static int passwPos = 0;
	
	// legal characters to be passed to other application
	static List<Integer> legal = Arrays.asList(76, 65, 32);
	
	// start and stop button
	static int startStop = 32;
	
	// maximum recording time IN MINUTES!
	static int recordingTime = 2;
	
	// for debug console printing
	static boolean debug = true;
	
	// transparency border
	private static int stdTrans = 180;
	// transparency center
	private static int centerTrans = 1;
	
	// border width
	private static int borderNorth = 200;
	private static int borderSouth = 200;
	private static int borderEast = 200;
	private static int borderWest = 200;
	
	static Thread captureThread;
	static boolean isRecording = false;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		loadProperties();

		SwingUtilities.invokeLater(new Runnable() {
			@SuppressWarnings("unused")
			@Override
			public void run() {
				new SecureGui();
			}
		});
		
		
		HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
		keyboardHook = new LowLevelKeyboardProc() {
			@Override
			public LRESULT callback(int nCode, WPARAM wParam, KBDLLHOOKSTRUCT info) {
				if (nCode >= 0) {
					switch (wParam.intValue()) {
					case WinUser.WM_SYSKEYUP:
					case WinUser.WM_KEYUP:
						// print out key code
						if(debug) {
							System.out.println("key: " + KeyEvent.getKeyText(info.vkCode) + " (" + info.vkCode + ")");
						}
						// check for password
						if(info.vkCode == passw[passwPos]) {
							passwPos++;
							if(passwPos == passw.length) {
								quitMe();
								return new LRESULT(1);
							}
						}
						else {
							passwPos = 0;
						}  
						// handle start/stop
						if(info.vkCode == startStop) {
							startStopCapture();
						}
						
					case WinUser.WM_KEYDOWN:
					case WinUser.WM_SYSKEYDOWN:
						// pass legal keys
						if(legal.contains(info.vkCode)) {
							return lib.CallNextHookEx(hhk, nCode, wParam, info.getPointer());
						}
						break;
					}
				}
				return new LRESULT(1);
			}
		};
		hhk = lib.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardHook, hMod, 0);
		if(debug) {
			System.out.println("Keyboard hook installed, type anywhere, 'q' to quit");
		}
		
		lib.GetMessage(new MSG(), null, 0, 0);
	}
	
	
	
	private static void loadProperties() {
		
		Properties properties = new Properties();
		try {
			BufferedInputStream stream = new BufferedInputStream(de.clemensloos.securemyapp.SecureGui.class.getResourceAsStream("res/secure.properties"));
			properties.load(stream);
			stream.close();
		} catch (FileNotFoundException e) {
			System.out.println("Properties file not found!");
			if(debug) {
				e.printStackTrace();
			}
			return;
		} catch (IOException e) {
			System.out.println("Error reading properties file!");
			if(debug) {
				e.printStackTrace();
			}
			return;
		}
		
		// passphrase
		String[] passwString = properties.getProperty("passw").split(",");
		passw = new int [passwString.length];
		int i=0;
		for(String s : passwString) {
			passw[i++] = Integer.parseInt(s);
		}
		
		// legal characters to be passed to other application
		String[] legalString = properties.getProperty("legal").split(",");
		legal = new ArrayList<Integer>();
		for(String s : legalString) {
			legal.add(Integer.parseInt(s));
		}
		
		// start and stop button
		startStop = Integer.parseInt(properties.getProperty("startStop"));
		
		// maximum recording time IN MINUTES!
		recordingTime = Integer.parseInt(properties.getProperty("recordingTime"));
		
		// for debug console printing
		debug = Boolean.parseBoolean(properties.getProperty("debug"));
		
		// transparency border
		stdTrans = Integer.parseInt(properties.getProperty("stdTrans"));
		// transparency center
		centerTrans = Integer.parseInt(properties.getProperty("centerTrans"));
		
		// border width
		borderNorth = Integer.parseInt(properties.getProperty("borderNorth"));
		borderSouth = Integer.parseInt(properties.getProperty("borderSouth"));
		borderEast = Integer.parseInt(properties.getProperty("borderEast"));
		borderWest = Integer.parseInt(properties.getProperty("borderWest"));
		
	}



	static void quitMe() {
		
		if(debug) {
			System.out.println("unhook and exit");
		}
		lib.UnhookWindowsHookEx(hhk);
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.exit(0);
			}
		}.start();
		
	}
	

	
	static void startStopCapture() {
		
		if(isRecording) {
			captureThread.interrupt();
			return;
		}
		

		captureThread = new Thread() {	
			@Override
			public void run() {
				try {
					Thread.sleep(60000 * recordingTime);
//					Thread.sleep( 10 *  1000 );
				} catch (InterruptedException e) {
					if(debug) {
						System.out.println("Recording stopped");
					}
					isRecording = false;
					return;
				}
				try {
					Robot robot = new Robot();
					robot.keyPress(startStop);
					robot.keyRelease(startStop);
				} catch (Exception e) {
					// ignore
				}
				if(debug) {
					System.out.println("Recording stopped due to time limit");
				}
				isRecording = false;
				return;   
			}
		};
		
		captureThread.start();
		if(debug) {
			System.out.println("Recording started");
		}
		isRecording = true;
	}
	
	

	SecureGui() {

		initGui();
	}

	static JFrame frame;
	
	private void initGui() {

		frame = new JFrame("SecureMyApp");
		frame.setUndecorated(true);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);		
		AWTUtilities.setWindowOpaque(frame, false);
		
		
		TransparentPanel north = new TransparentPanel(stdTrans);
		TransparentPanel south = new TransparentPanel(stdTrans);
		TransparentPanel east = new TransparentPanel(stdTrans);
		TransparentPanel west = new TransparentPanel(stdTrans);
		TransparentPanel center = new TransparentPanel(centerTrans, true);
		
		north.setPreferredSize(new Dimension(1, borderNorth));
		south.setPreferredSize(new Dimension(1, borderSouth));
		east.setPreferredSize(new Dimension(borderEast, 1));
		west.setPreferredSize(new Dimension(borderWest, 1));
		
		frame.setLayout(new BorderLayout());
		
		frame.add(north, BorderLayout.NORTH);
		frame.add(south, BorderLayout.SOUTH);
		frame.add(east, BorderLayout.EAST);
		frame.add(west, BorderLayout.WEST);
		frame.add(center, BorderLayout.CENTER);
		

		frame.setAlwaysOnTop(true);
		frame.setFocusableWindowState(false);
		frame.setExtendedState(Frame.MAXIMIZED_BOTH);
		frame.setVisible(true);
		
		
	}
	

	class TransparentPanel extends JPanel {
		
		private static final long serialVersionUID = 1L;
		int opacity = 0;
		
		TransparentPanel(int opacity) {
			super();
			this.opacity = opacity;
		}
		
		TransparentPanel(final int opacity, boolean wait) {
			super();
			if( ! wait) {
				this.opacity = opacity;
			}
			else {
				new Thread() {
					@Override
					public void run() {
						try {
							Thread.sleep(5000);
						} catch (Exception e) {
							e.printStackTrace();
						}
						if(debug) {
							System.out.println("Screen locked!");
						}
						TransparentPanel.this.opacity = opacity;
						TransparentPanel.this.repaint();
					}
				}.start();
			}
		}
		
		@Override
		protected void paintComponent(final Graphics g) {
			Color bg = new Color(0, 0, 0, opacity);
			g.setColor(bg);
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		
	}


}
