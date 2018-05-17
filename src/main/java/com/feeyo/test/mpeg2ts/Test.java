package com.feeyo.test.mpeg2ts;

public class Test {
	
	public static void main(String[] args) {
		
		byte bb1 = 0x00;
		byte bb2 = (byte)0xff;
		long ext = ((long) (bb1 & 0x001) << 8) | ((long) (bb2 & 0x0ff));
		System.out.println( ext );
		
		byte b1 = 0x10 | ((1) & 0xF);
		System.out.println( b1 & 0xff);
		
		b1 |= 0x20;
		
		System.out.println( b1 & 0xff);
		
		int adaptation_field_control 		= 	((b1 & 0xFF) >> 4) & 0x03;					
		int continuity_counter 				=	(b1 & 0xFF) & 0xF;		
		
		
		System.out.println( b1 & 0xff );
		
		byte b = 0x35;  // 0x50 0x10 0x40 
		
		int discontinuity_indicator 				= (b & 0x80) >> 7;
		int random_access_indicator 			 	= (b & 0x40) >> 6;
		int elementary_stream_priority_indicator 	= (b & 0x20) >> 5;
		int pcr_flag							 	= (b & 0x10) >> 4;
		int opcr_flag							 	= (b & 0x08) >> 3;
		int splicing_point_flag 				 	= (b & 0x04) >> 2;
		int transport_private_data_flag 		 	= (b & 0x02) >> 1;
		int adaptation_field_extension_flag 	 	= b & 0x01;
		
	
		System.out.println( "xxxxxx" );
	}

}
