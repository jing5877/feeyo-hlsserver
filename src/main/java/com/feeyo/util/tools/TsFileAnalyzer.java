package com.feeyo.util.tools;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import com.feeyo.mpeg2ts.Pat;
import com.feeyo.mpeg2ts.Pes;
import com.feeyo.mpeg2ts.Pmt;
import com.feeyo.mpeg2ts.Ts;
import com.feeyo.mpeg2ts.TsReader;
import com.feeyo.mpeg2ts.util.TsUtil;

/**
 * MPEG-TS 分析器
 * 
 * @author zhuam
 *
 */
public class TsFileAnalyzer extends JFrame {

	private static final long serialVersionUID = 8323989322508018475L;
	
	private Preferences prefs = Preferences.userRoot().node( TsFileAnalyzer.class.getName() );
	
	// top
	private JTable tsTable;
	private String[] columnNames = {"No", "type", "PID", "isPesStart", "isScrambled", "adaptation", "cCounter", "PCR", "PTS", "DTS"};
	private int[] colunmWidths = {50, 100, 70, 80, 80, 80, 80, 120, 100, 100};
	private Object[][] rowData = {};
	
	// bottom
	private JPanel mpegTsPanel;
	private JTextArea mpegTsTextArea;

	
	private TsReader tsReader = new TsReader();
	private Ts[] tsArray = null;
	
	
    private JPanel createPanelForComponent(JComponent comp) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(comp, BorderLayout.CENTER);
		return panel;
	}
    
    
    public void initLayout() {

    	final JTextField tsFileNameTextField = new JTextField();	
    	tsFileNameTextField.setPreferredSize(new Dimension(400, 35));	
    	tsFileNameTextField.setEnabled(false);
		
    	
    	
    	Button tsLoadButton = new Button();
    	tsLoadButton.setLabel("Load MPEG-TS files");
    	tsLoadButton.addActionListener( new ActionListener() {

    		String lastPath = prefs.get("last_path", "");
    		
			@Override
			public void actionPerformed(ActionEvent e) {
					
				JFileChooser jfc = new JFileChooser( lastPath );
				jfc.setFileFilter( new FileFilter(){
					public boolean accept(File f){
						if (f.isDirectory())
							return true;
						else if (f.getName().endsWith(".ts"))
							return true;
						else
							return false;
				    }
					
				    public String getDescription(){
				        return "ts files";
				    }
				});
				
				if (jfc.showOpenDialog( TsFileAnalyzer.this ) == JFileChooser.APPROVE_OPTION ) {
					File f =  jfc.getSelectedFile();
					prefs.put("last_path", f.getPath());
					
					try {
						
						tsFileNameTextField.setText( f.getPath() );
						
						byte[] buff = Files.readAllBytes( f.toPath() );
						tsArray = tsReader.parseTsPacket( buff );
						
						// 
						rowData = new Object[tsArray.length][10];
						for(int i = 0; i < tsArray.length; i++) {
							Ts ts = tsArray[i];
							rowData[i][0] = i;
							
							if (ts instanceof Pes) {
								Pes pes = (Pes) ts;

								if (pes.stream_id == Ts.AUDIO_STREAM_ID) {
									rowData[i][1] = "PES/(Audio)";

								} else if (pes.stream_id == Ts.VIDEO_STREAM_ID) {
									rowData[i][1] = "PES/(Video)";

								} else {
									rowData[i][1] = "PES";
								}
								
								rowData[i][7] = pes.adaptation_filed == null ? 0 : pes.adaptation_filed.getPCR();
								rowData[i][8] = pes.pts;
								rowData[i][9] = pes.dts;
								
							} else {

								if (ts instanceof Pat) {
									rowData[i][1] = "PAT";
								} else if (ts instanceof Pmt) {
									rowData[i][1] = "PMT";
								} else {
									rowData[i][1] = "UNKNOW";
								}

								rowData[i][7] = 0;
								rowData[i][8] = 0;
								rowData[i][9] = 0;
							}
								
							
							rowData[i][2] = ts.PID;
							rowData[i][3] = ts.payload_unit_start_indicator;	
							rowData[i][4] = ts.transport_scrambling_control;
							rowData[i][5] = ts.adaptation_field_control;	
							rowData[i][6] = ts.continuity_counter;	
						}
						
						TableModel dataModel = new DefaultTableModel(rowData, columnNames) {
							private static final long serialVersionUID = 1L;
							@Override
							public boolean isCellEditable(int row, int column) {
								return false;
							}
						};			
						tsTable.setModel(dataModel);

						for (int i = 0; i < colunmWidths.length; i++) {
							tsTable.getColumnModel().getColumn(i).setPreferredWidth(colunmWidths[i]);
						}
						tsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
						
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}

			}
    	});
		

		tsTable = new JTable(rowData, columnNames);
		tsTable.setRowSelectionAllowed(true);
		tsTable.setColumnSelectionAllowed(false);
		tsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tsTable.setBorder( BorderFactory.createLineBorder(Color.BLACK, 0) );
		
		tsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 2842108295158264818L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int col) {
				if ( tsArray != null && row < tsArray.length ) {
					Ts ts = tsArray[ row ];
					final Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
					c.setBackground( ts != null && ts instanceof Pes && ts.payload_unit_start_indicator == 1 
							? new Color(255, 255, 200) : Color.WHITE);
				}
				return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
			}
		});
		tsTable.addMouseListener(new MouseAdapter() {
			
			private String toHex(int v) {
				String hex = "0x" + String.format("%2s", Integer.toHexString(v)).replace(' ', '0');
				return hex;
			}
			
			private String getTsString(Ts ts) {
				
				StringBuffer fieldBuffer = new StringBuffer(500);
				
				Class<?> clazz = ts.getClass();
    			Field[] fields = clazz.getDeclaredFields();
				for (int i = 0; i < fields.length; i++) {
					
					try {
						Field field = fields[i];
						field.setAccessible(true);
						
						String name = field.getName() ;
						Object value = field.get( ts );
						
						// skip
						if ( "adaptation_filed".equals( name ) ) {
							continue;
						}

						if ( "program_map_PID".equals( name ) && value != null ) {
							
							int[] pmtIds = (int[]) value;
							for(int j=0; j<pmtIds.length; j++) {
								fieldBuffer.append("program_map_PID=").append(  pmtIds[j] ).append("\r\n");
							}
							
						} else if ( "descriptors".equals( name ) && value != null ) {
							
							fieldBuffer.append("\r\n");
							fieldBuffer.append("descriptors\r\n");
							
							@SuppressWarnings("unchecked")
							List<Pmt.Descriptor> descriptors = (List<Pmt.Descriptor>)value;
							for(Pmt.Descriptor descriptor: descriptors) {
								fieldBuffer.append("tag=").append(  descriptor.tag ).append("\r\n");
								fieldBuffer.append("content=").append( new String(descriptor.rawData) ).append("\r\n");
								fieldBuffer.append("\r\n");
							}
							
						} else if ( "streams".equals( name ) && value != null ) {
							
							fieldBuffer.append("\r\n");
							fieldBuffer.append("streams\r\n");
							
							@SuppressWarnings("unchecked")
							List<Pmt.Stream> streams = (List<Pmt.Stream>)value;
							for(Pmt.Stream stream: streams) {
								fieldBuffer.append("stream_type=").append(  stream.stream_type ).append("\r\n");
								fieldBuffer.append("elementary_PID=").append(  stream.elementary_PID ).append("\r\n");
								fieldBuffer.append("descriptor=").append(  stream.descriptor ).append("\r\n");
								fieldBuffer.append("ES_info_length=").append(  stream.ES_info_length ).append("\r\n");
								fieldBuffer.append("reserved5=").append(  stream.reserved5 ).append("\r\n");
								fieldBuffer.append("reserved6=").append(  stream.reserved6 ).append("\r\n");
								fieldBuffer.append("\r\n");

							}
							
						} else if ( "es_data".equals( name ) && value != null ) {
							
							byte[] bb = (byte[])value;
							String esHexString = TsUtil.hexString(bb, 0, bb.length);
							
							fieldBuffer.append("\r\n");
							fieldBuffer.append("ES_data \r\n");
							fieldBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
							fieldBuffer.append( esHexString );
							fieldBuffer.append("\r\n");
							
						} else {
							if ( value != null ) {
								fieldBuffer.append(name).append("=").append( value ).append("\r\n");
							}
						}
						
						
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				return fieldBuffer.toString();
			}
			
			public void mousePressed(MouseEvent me) {
		        
		        if ( me.getClickCount() == 1 ) {
		        	
		        	JTable table =(JTable) me.getSource();
		        	int rowIndex = table.getSelectedRow();
		        	int columnIndex = 0;		        	
		        	Object obj = table.getModel().getValueAt(rowIndex, columnIndex);
		        	if ( obj != null && tsArray != null ) {	
		        		int idx = (int)obj;
		        		Ts ts = tsArray[idx];
		        		String hexFullStr = TsUtil.hexString( ts.full_data, 0, ts.full_data.length);
		        		
	
		        		//
		        		StringBuffer tsStrBuffer = new StringBuffer(500);
		        		
		        		// ts header info
		        		tsStrBuffer.append("\r\n");
		        		tsStrBuffer.append("TS_header\r\n");
		        		tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
		        		tsStrBuffer.append("sync_byte=").append( toHex( ts.sync_byte ) ).append("\r\n");
		        		tsStrBuffer.append("transport_error_indicator=").append(  ts.transport_error_indicator ).append("\r\n");
		        		tsStrBuffer.append("payload_unit_start_indicator=").append(  ts.payload_unit_start_indicator ).append("\r\n");
		        		tsStrBuffer.append("transport_priority=").append(  ts.transport_priority ).append("\r\n");
		        		tsStrBuffer.append("PID=").append( ts.PID ).append("\r\n");
		        		tsStrBuffer.append("transport_scrambling_control=").append(  ts.transport_scrambling_control ).append("\r\n");
		        		tsStrBuffer.append("adaptation_field_control=").append(  ts.adaptation_field_control ).append("\r\n");
		        		tsStrBuffer.append("continuity_counter=").append(  ts.continuity_counter ).append("\r\n");
		        		
		    
		        		tsStrBuffer.append("\r\n");
						if ( ts instanceof Pat) {
							
							tsStrBuffer.append("PAT_header\r\n");
							tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
							
							tsStrBuffer.append( getTsString(ts) );
							
						} else if ( ts instanceof Pmt) {
							
							tsStrBuffer.append("PMT_header\r\n");
							tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
							
							tsStrBuffer.append( getTsString(ts) );
							
						} else if ( ts instanceof Pes) {
	
							Pes pes = (Pes)ts;
							
							if ( pes.adaptation_filed != null ) {
								
								tsStrBuffer.append("Adaptation_filed\r\n");
								tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
								tsStrBuffer.append("discontinuity_indicator=").append( pes.adaptation_filed.discontinuity_indicator ).append("\r\n");
								tsStrBuffer.append("random_access_indicator=").append( pes.adaptation_filed.random_access_indicator ).append("\r\n");
								tsStrBuffer.append("elementary_stream_priority_indicator=").append( pes.adaptation_filed.elementary_stream_priority_indicator ).append("\r\n");
								tsStrBuffer.append("pcr_flag=").append( pes.adaptation_filed.pcr_flag ).append("\r\n");
								tsStrBuffer.append("opcr_flag=").append( pes.adaptation_filed.opcr_flag ).append("\r\n");
								tsStrBuffer.append("splicing_point_flag=").append( pes.adaptation_filed.splicing_point_flag ).append("\r\n");
								tsStrBuffer.append("transport_private_data_flag=").append( pes.adaptation_filed.transport_private_data_flag ).append("\r\n");
								tsStrBuffer.append("adaptation_field_extension_flag=").append( pes.adaptation_filed.adaptation_field_extension_flag ).append("\r\n");
								tsStrBuffer.append("program_clock_reference_base=").append( pes.adaptation_filed.program_clock_reference_base ).append("\r\n");
								tsStrBuffer.append("program_clock_reference_extension=").append( pes.adaptation_filed.program_clock_reference_extension ).append("\r\n");
								tsStrBuffer.append("PCR=").append( pes.adaptation_filed.getPCR() ).append("\r\n");
								tsStrBuffer.append("PCR_TIME=").append( pes.adaptation_filed.getPCR_TIME() ).append("\r\n");
								tsStrBuffer.append("\r\n");
							}
							
							if ( pes.payload_unit_start_indicator == 0 ) {
								
								byte[] bb = pes.es_data;
								String esHexString = TsUtil.hexString(bb, 0, bb.length);
								
								tsStrBuffer.append("\r\n");
								tsStrBuffer.append("ES_data \r\n");
								tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
								tsStrBuffer.append( esHexString );
								tsStrBuffer.append("\r\n");
								
							} else {
								tsStrBuffer.append("PES_header\r\n");
								tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
								tsStrBuffer.append( getTsString(ts) );
							}

						}
						
						tsStrBuffer.append("\r\n");
						tsStrBuffer.append("HEX \r\n");
						tsStrBuffer.append("+++++++++++++++++++++++++++++++++++++\r\n");
						tsStrBuffer.append( hexFullStr );
						tsStrBuffer.append("\r\n");
						
						mpegTsTextArea.setText( tsStrBuffer.toString()  );

		        	}
		        }
		    }
			
		});
		

		// 主布局
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout( new BorderLayout() );
		
		// 顶部布局
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS)); //new GridLayout(0, 2)
		topPanel.add( tsFileNameTextField );
		topPanel.add( tsLoadButton );
		
		mpegTsPanel = new JPanel();
		mpegTsPanel.setLayout(new BorderLayout(2,1));
		mpegTsPanel.setBackground( Color.WHITE );
		mpegTsPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		
		mpegTsTextArea = new JTextArea();
		mpegTsTextArea.setFont(new Font("monospaced", Font.PLAIN, 12));
		mpegTsTextArea.setDoubleBuffered(true);
		mpegTsPanel.add( mpegTsTextArea );
		
		
		// split panel
		// +++++++++++++++++++++++++++++++++++++++++
		JScrollPane topJScrollPane = new JScrollPane ( tsTable );
		topJScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);	
		
		JScrollPane bottomPane = new JScrollPane ( mpegTsPanel );
		bottomPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);	
		
        JSplitPane upDownSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
        		createPanelForComponent(topJScrollPane), createPanelForComponent(bottomPane));
        
		upDownSplitPane.setOneTouchExpandable(true);
		upDownSplitPane.setDividerLocation(300);		
		mainPanel.add(upDownSplitPane);
		// +++++++++++++++++++++++++++++++++++++++++

        this.add(topPanel, BorderLayout.NORTH );
        this.add(mainPanel );
        

		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setTitle("MPEG-TS Analyzer 0.1alpha");
		this.setResizable(false);	
		this.setPreferredSize(new Dimension(800, 680));
		this.pack();
		this.setLocationRelativeTo(null);
		this.setVisible(true);
		
		this.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent windowEvent) {
				System.exit(0);
			}
		});
		
	}
	
	public static void main(String[] args) {
		TsFileAnalyzer tsAnalyzer = new TsFileAnalyzer();
		tsAnalyzer.initLayout();
	}

}