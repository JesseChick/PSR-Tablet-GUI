package com.psrt.serial;

//import static com.psrt.threads.SerialMonitor.log;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import com.psrt.entities.systems.LogMonitor;
import com.psrt.threads.SerialMonitor;

import jssc.SerialPortException;

/**
 * New SerialReader class based on jssc. Reads in serial data from the COM port given in the SerialMonitor class (for now). 
 * Future usage would give the ability to search the ports for a given behavior.
 * Currently the read function of this class is called on its own thread to fill an internal FIFO buffer with serial data in the form of bytes. 
 * The bytes are converted to unsigned integers for readability (this may be changed later, and probably will be) and placed into the FIFO buffer.  
 * The read function then calls the SerialParser's cut method in order to cut the newly read in data into pieces, given by the stop bits 
 * (FF FF FF FF FE FE FF FF FF FF). These pieces are then converted back into unsigned bytes and placed
 * into another FIFO buffer of byte arrays, which are then parsed by the SerialParser's parse method.  
 * This parsed information is then passed onto the GUI via magicks.
 * @author Austin Dibble
 *
 */
public class SerialReader2 {
	
	/*************************************
				PRIVATE FIELDS
	**************************************/	
	 private CircularFifoQueue<Integer> internalBuffer;
	
    /*************************************
				PUBLIC FIELDS
 	**************************************/		 
	 public jssc.SerialPort seport;
	 public SerialParser sp;
	 public long lastUpdate;
	 //public long updateDelta = 0;
	 
	 
	 public SerialReader2(SerialParser sp, CircularFifoQueue<Integer> internalBuffer){
		 this.sp = sp;
		 this.internalBuffer = internalBuffer;
		 LogMonitor.log("Opening serial reader.", LogMonitor.LogType.SERIAL_READER);
		 findPort();
		 lastUpdate = System.currentTimeMillis();
		 LogMonitor.log("Port opened.", LogMonitor.LogType.SERIAL_READER);
	 }
	 
	 public boolean findPort(){
		 String[] portNames = jssc.SerialPortList.getPortNames();
		 
		 if(portNames.length > 0){
			 for(String port : portNames){
				 if(!port.equals("COM3")){
					 LogMonitor.log("Port name: " + port, LogMonitor.LogType.SERIAL_READER);
					 seport = new jssc.SerialPort(port);
					 
					 if(validatePort()) break;
				 }else{
					 LogMonitor.log("Got port 3. Skipping.", LogMonitor.LogType.SERIAL_READER);
				 }
			 }
		 }else{
			 LogMonitor.log("No ports...", LogMonitor.LogType.SERIAL_READER);
		 }
		return true;
	 }
	 
	 private boolean validatePort(){
		 if(seport != null){
			 try{
				 LogMonitor.log("Opening port", LogMonitor.LogType.SERIAL_READER);
				 seport.openPort();
				 seport.setParams(jssc.SerialPort.BAUDRATE_115200, 
                        jssc.SerialPort.DATABITS_8,
                        jssc.SerialPort.STOPBITS_1,
                        jssc.SerialPort.PARITY_NONE);
			 }catch(jssc.SerialPortException e){
				 //e.printStackTrace();
				 LogMonitor.log("Couldn't open serial port...", LogMonitor.LogType.SERIAL_READER);
				 return false;
			 }
		 }else{
			 LogMonitor.log("Serial port not found. Aborting", LogMonitor.LogType.SERIAL_READER);
			 return false;
		 }
		 
		 if(!hasMarker()) return false;
		 else return true;
	 }
	 
	 private boolean hasMarker(){
		 try {
			if(seport.getInputBufferBytesCount() > 0){
				 byte[] b = seport.readBytes(SerialMonitor.MAX_CHECK_SIZE);
				 
				 for(int i = 0; i < b.length; i++){
					 if(marker_bytes(b, i)){
						 return true;
					 }
				 }
			 }
		 } catch (Exception e) {
			e.printStackTrace();
			return false;
		 }
		 return false;
	 }
	 
	 /**
	  * Reads the information from the hardware buffer or whatever I don't really understand how all that works! 
	  * Just gets byte data from the COM port and passes it to the 
	  * input buffer that is checked by the SerialParser....
	  */
	 //int i = 0;
	 public void read(){
		try {
			if(seport.getInputBufferBytesCount() > 0){
				 byte[] b = seport.readBytes(1);
				 int num = unsign(b[0]);
				 //log("Reading: " + num);
				 internalBuffer.add(num);
				 //updateDelta = System.currentTimeMillis() - lastUpdate;
				 lastUpdate = System.currentTimeMillis();
			}else{
				//updateDelta = System.currentTimeMillis() - lastUpdate;
				
			}
		} catch (SerialPortException e) {
			e.printStackTrace();
			LogMonitor.log("Couldn't read data.", LogMonitor.LogType.SERIAL_READER);
		}
		sp.cut();
	 }
	 
	 /**
	  * Closes the currently open serial port. This SerialReader object should probably not be used after this
	  */
	 public void close(){
		 try {
			if(seport != null) seport.closePort();
		 } catch (SerialPortException e) {
			//e.printStackTrace();
			LogMonitor.log("Couldn't close port.", LogMonitor.LogType.SERIAL_READER);
		 }
	 }
	 
	 
	 
	 /**************************************************************************
									UTILITIES (Byte interpretation)
	 ***************************************************************************/
	
	 /**
	 * Does what it says. But it turns the byte into an int. Java doesn't have unsigned bytes. Wow
	 * @param b
	 * @return
	 */
	 private int unsign(byte b){ return b & 0xFF;}
	 
	 
	 private boolean marker_bytes(byte[] bytes, int i){
			boolean end = false;
			if(bytes[i]     == 0xFF &&
			   bytes[i + 1] == 0xFF && 
			   bytes[i + 2] == 0xFF &&
			   bytes[i + 3] == 0xFF && 
			   bytes[i + 4] == 0xFE &&
			   bytes[i + 5] == 0xFE &&
			   bytes[i + 6] == 0xFF &&
			   bytes[i + 7] == 0xFF && 
			   bytes[i + 8] == 0xFF &&
			   bytes[i + 9] == 0xFF) 
			{	
				end = true;
			}
			return end;
	 }
}
