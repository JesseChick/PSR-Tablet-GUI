package com.psrt.serial;

import static com.psrt.threads.SerialMonitor.log;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import com.psrt.containers.PDBCSVInfo;
import com.psrt.containers.PDBID;
import com.psrt.containers.PDBValue;
import com.psrt.containers.PDBValue.PDBValueType;
import com.psrt.containers.values.PDBFloatValue;
import com.psrt.containers.values.PDBIntValue;
import com.psrt.entities.components.DepositBox;
import com.psrt.entities.systems.Bank;
import com.psrt.guitabs.BMSTab;
import com.psrt.threads.SerialMonitor;


//-----------------------------------------------------------------###################################----------------------------------------------------------
/**
 * Parses serial data.  Holds the cut function (Currently called by the SerialReader) and the parse function.  Together they cut the data out from between the frame markers
 * and parse it into its useful information based on the CAN dictionary.
 * @author Austin Dibble
 *
 */
 public class SerialParser{
	 /**
	  * <b>MarkerState</b> - debug states for frame marker 
	  * <p>MARKER - Uses this state to detail that the current index is a frame marker</p>
	  * <p>NOT_MARKER - Uses this state to detail that the current index isn't a frame marker</p>
	  * <p>END_OF_BUFFER - Uses this state in order to describe that the current position couldn't be checked because one of the position checks goes out of bounds</p>
	  * <p>This enum is used primarily by {@link SerialParser.cut()}
	  * @author Austin Dibble
	  *
	  */
	 public static enum MarkerState{
	 	 NOT_MARKER,
	 	 IS_MARKER,
	 	 END_OF_BUFFER;
	 }
	 
	 /*************************************
	 			PRIVATE FIELDS
	 **************************************/

	 private CircularFifoQueue<Integer> internalBuffer;
	 private CircularFifoQueue<byte[]> parseBuffer;
	 
	 
	 private Bank bank;
	 
	 /*************************************
				PUBLIC FIELDS
	 **************************************/
	 public static final boolean SERIAL_PARSE_DEBUG = false;
	 public static final boolean SERIAL_CUT_DEBUG = false;
	 
	 
	 
	 /**
	  * Creates a new SerialParser instance.  These objects should be monitored within the SerialMonitor class (though there should
	  * likely be only one instance of this).  It should also be given its own thread.  Overall, its two main functions are cutting
	  * and parsing the serial data read into the {@link CircularFifoQueue} <b>internalBuffer</b> by another class, probably the closely-related
	  * {@link SerialReader2} class.  
	  * @param internalBuffer - buffer that will hold the data to be parsed/cut.
	  * @param bank - {@link Bank} object for placing parsed data.
	  */
	 public SerialParser(CircularFifoQueue<Integer> internalBuffer, Bank bank){
		 this.bank = bank;
		 this.internalBuffer = internalBuffer;
		 log("Initializing serialParser");
		 initialize();
	 }
	 
	 /**
	  * Starts things going. Should be called in the constructor. 
	  * <p> List of items also initialized here:</p>
	  * <p><ul>
	  * <li>{@link CircularFifoQueue} reference, parseBuffer
	  * </ul></p>
	  */
	 private void initialize(){
		 parseBuffer = new CircularFifoQueue<byte[]>(512);
	 }
	 
		
	 /**************************************************************************
									BYTE ARRAY CUTTING
	 ***************************************************************************/
	 
	 int m1 = -1;
	 int m2 = -1;
	 int index = 0;
	 
	 /**
	  * Attempts to cuts the serial data lying in the internalBuffer. 
	  * Must I explain this? I barely remember how it works. Maybe later
	  */
	 public void cut(){
		 //log("Frames in parseBuffer: " + parseBuffer.size());
		 if(SERIAL_CUT_DEBUG) {
			 log("Mark1: " + m1);
			 log("Mark2: " + m2);
		 }
		 if(!internalBuffer.isEmpty()){
			 for(; true; ){
				 if(SERIAL_CUT_DEBUG) {
					 log("Checking index: " + index);
				 }
				 MarkerState mark1 = marker(internalBuffer, index);
				 if(mark1 == MarkerState.END_OF_BUFFER) {
					 if(SERIAL_CUT_DEBUG) {
						 log("End of buffer...");
					 }
					 //index++;
					 break;
				 }
				 else if(mark1 == MarkerState.IS_MARKER) {
					 if(SERIAL_CUT_DEBUG) {
						 print_range(index, index + 10);
					 }
					 if(m1 == -1) {
						 m1 = index + 10;
						 if(SERIAL_CUT_DEBUG) {
							 log("Found mark1 at " + m1);
						 }
						 index = m1;
					 }
					 else {
						 m2 = index + 10;
						 if(SERIAL_CUT_DEBUG) {
							 log("Found mark2 at " + m2);
						 }
						 int delta = (m2 - m1) - 10;
						 if(SERIAL_CUT_DEBUG) {
							 log("M1: " + m1 + ", M2: " + m2 + " - delta: " + delta);
						 }
						 if(delta % 10 == 0){
							 if(SERIAL_CUT_DEBUG) {
								 log("Adding byte array to parseBuffer of length " + delta);
							 }
							 byte[] temp = new byte[delta];
							 for(int i = 0; i < delta; i++){
								 int temp_int = internalBuffer.get(m1 + i);
								 temp_int -= 128;
								 byte temp_byte = (byte) temp_int;
								 temp[i] = temp_byte;
							 }
							 parseBuffer.add(temp);
							 if(SERIAL_CUT_DEBUG) log("Parsebuffer size; cut: " + parseBuffer.size());
							 for(int i = 0; i < m2 - 10; i++){
								 internalBuffer.remove();
							 }
							 index = 10;
							 m1 = 10;
							 m2 = -1;
						 }else{
							 if(SERIAL_CUT_DEBUG) {
								 log("Data not a multiple of 10. Discarding frame.");
							 }
							 //delete index 0 through (m2 - 10)
							 for(int i = 0; i < m2 - 10; i++){
								 internalBuffer.remove();
							 }
							 index = 10;
							 m1 = 10;
							 m2 = -1;
						 }
					 }
				 }
				 else if(mark1 == MarkerState.NOT_MARKER) {
					 if(m1 == -1){
						 if(SERIAL_CUT_DEBUG) {
							 log("Not found+pl: " + internalBuffer.poll());
						 }else{
							 internalBuffer.poll();
						 }
						 index = 0;
					 }else{
						 if(SERIAL_CUT_DEBUG) {
							 log("Not found+pk: " + internalBuffer.get(index));
						 }
						 index++;
						 if(index - m1 >= SerialMonitor.MAX_CHECK_SIZE){
							 log("Error in SerialParser.cut(): Distance between current index and 1st check marker is greater than max check size (" + SerialMonitor.MAX_CHECK_SIZE + ")");
							 log("Starting frame check over.");
							 index = 0; 
							 m1 = -1; 
							 m2 = -1;
						 }
					 }
				 }
			 }
	 	 }
	 }
	 
	 /**
	  * Looks the given FIFO buffer at the given index i to see if there is a marker at that position.
	  * @param bytes - FIFO buffer to check
	  * @param i - index in buffer to check
	  * @return - {@link MarkerState} to describe exit condition.
	  */
	 private MarkerState marker(CircularFifoQueue<Integer> bytes, int i){
			MarkerState status = MarkerState.NOT_MARKER;
				try{
					if(bytes.get(i)  == 0xFF){
						if(SERIAL_CUT_DEBUG) log("Found FF, checking bytes...");
				 		if(bytes.get(i + 1) == 0xFF && 
						   bytes.get(i + 2) == 0xFF &&
						   bytes.get(i + 3) == 0xFF && 
						   bytes.get(i + 4) == 0xFE &&
						   bytes.get(i + 5) == 0xFE &&
						   bytes.get(i + 6) == 0xFF &&
						   bytes.get(i + 7) == 0xFF && 
						   bytes.get(i + 8) == 0xFF &&
						   bytes.get(i + 9) == 0xFF) 
						{	
							status = MarkerState.IS_MARKER;
						}else{
							status = MarkerState.NOT_MARKER;
						}
					}else{
						status = MarkerState.NOT_MARKER;
					}
							  
				}catch(NoSuchElementException e){
					//log("Reached the end of the buffer in marker, waiting...");
					status = MarkerState.END_OF_BUFFER;
					return status;
				}
			return status;
		}
	 
	 
		
	 /**************************************************************************
							10-BYTE PARSING AND PACKAGING
	 ***************************************************************************/

	 /**
	  * Esta es muy complicado. 
	  * Not really, I guess.
	  * The bytes that this parse function grabs should already have the end markers removed, and be cut into multiples of ten bytes (multiple messages)
	  * then it takes those and cuts them into chunks of ten, and for every ten gets the info from them
	  */
	 @SuppressWarnings("rawtypes")
	public void parse(){
		 //log("parsing");
		 int l = parseBuffer.size();
		 //if(parse_debug) log("Parsebuffer size: " + l);
		 if(l > 0){
			 byte[] bytes = parseBuffer.poll();
			 if(SERIAL_PARSE_DEBUG) print_array(bytes);
			 if(bytes != null){
				 
				 int messages = bytes.length / 10;
				 if(SERIAL_PARSE_DEBUG) log("Num messages: " + messages); 
				 
				 DepositBox box = new DepositBox(messages);
				 
				 for(int i = 0; i < messages; i++){
					 //String reference = "-1";
					 //String num = "null";
					 int pos = i * 10;
					 int id = getID(bytes, pos);
					 
					 if(SERIAL_PARSE_DEBUG) log("ID: " + id);
					 
					 if(id >= 0x600 /*1536*/&& id <= 0x6FF /*1791*/){ //BMS Crap
						 if(SERIAL_PARSE_DEBUG) log("BMS ID found " + id);
						 
						 byte[] data_bytes = subArray(bytes, pos + 2, 8);
						 BMSTab.BMS_TREE(id, data_bytes, box);
						 
					 }else{ //PDBCAN crap
						 int function = getFunction(bytes, pos);
						 if(SERIAL_PARSE_DEBUG) log("Function: " + function);
						 
						 for(int j = 1; j <= bank.getDictionary().numActiveEntries(); j++){
							 PDBID identifier = new PDBID(id, function, j); 
							 PDBValue value = null; 
							 PDBCSVInfo csvInfo = bank.getDictionary().getParsedDictionary().get(identifier);
							 if(csvInfo == null) continue;
							 PDBValueType type = csvInfo.getType();
							 if(SERIAL_PARSE_DEBUG) {
								 log("SerialParser.parse() - CanID: ID = " + identifier.id + " | Function = " + identifier.function + " | Entry: " + identifier.entry + " | St. Index: " + csvInfo.startIndex());
								 if(type == null) log("SerialParser.parse() - Type null");
							 }
							 if(type == PDBValueType.FLOAT){
								 byte[] floatBytes = subArray(bytes, pos + csvInfo.startIndex(), 4);
								 value = new PDBFloatValue(bytesToFloat(floatBytes, 0), floatBytes);
								 //log("FloatValue: " + value.getValue());
							 }else if(type == PDBValueType.BYTE){
								 value = new PDBIntValue(bytes[csvInfo.startIndex()] + 128, subArray(bytes, pos + csvInfo.startIndex(), 1));
								 //log("ByteValue: " + (bytes[csvInfo.startIndex()] + 128));
								 //log("ByteValue: " + (value.getValue().intValue()));
							 }
							 if(value != null) {
								 box.put(identifier, value);
								 if(SERIAL_PARSE_DEBUG) log("SerialParser.parse(): " + identifier.hashCode());
							 }
						 }
					 }
				 }
				 bank.addBox(box);
			 }
		 }
	 }

	 
		
	 /**************************************************************************
								UTILITIES (BYTE ARRAY PARSING)
	 ***************************************************************************/
	 
	 /**
	  * GET THE FUNCTION DATA!!! Grabs the function data. Out of a 10 byte message, it should be the 2nd from the 0 position (index 2, in arrays)
	  * @param bytes
	  * @param pos
	  * @return
	  */
	 private int getFunction(byte[] bytes, int pos) {
		return bytes[pos + 2] + 128;
	 }
	
	 /**
	  * Gets the "ID" based on the position in the bytes. Should be the first two bytes out of ten.
	  * @param bytes - byte array
	  * @param pos - position
	  * @return
	  */
	private int getID(byte[] bytes, int pos) {
		 //bytes[pos], bytes[pos+1]
		 byte id1 = bytes[pos]; //0011
		 byte id2 = bytes[pos + 1]; //1010   		 -> \
		 int int_id1 = (id1 + 128) << 8; //0011 0000     \
		 int id = int_id1 | (id2 + 128); //0011 1010 <-*
		 //byte b_combo = (byte)combo;
		 if(SERIAL_PARSE_DEBUG){
			 log("ID1: " + id1);
			 log("ID2: " + id2);
			 log("int_id1: " + int_id1);
			 log("ID: " + id);
		 }
		return id;
	}

	
	 /**************************************************************************
								UTILITIES (BYTE MANIPULATION)
	 ***************************************************************************/
	
	/**
	 * Does what it says, though I have no idea if it works yet. Stay tuned
	 * @param bytes
	 * @return
	 */
	public static int bytesToInt(byte... bytes){
		int num = -101;
		 try{
			 num = ByteBuffer.wrap(bytes).getInt();
		 }catch(Exception e){
			 e.printStackTrace();
		 }
		 return num;
	 }
	
	private byte[] subArray(byte[] b, int start, int len){
		byte[] n = new byte[len];
		for(int i = 0; i < len; i++){
			n[i] = b[i + start];
		}
		return n;
	}
	 
	/**
	 * Does what it says. YAY GOOGLE. This piece of trash function took me 5 HOURS of Googling. 5!
	 * @param bytes - byte array, suckah
	 * @param index - index to start at in the byte array
	 * @return
	 */
	 public static float bytesToFloat(byte[] bytes, int index){
		 return Float.intBitsToFloat(((bytes[index] + 128) << 24) | (((bytes[index + 1] + 128)) << 16) | (((bytes[index + 2] + 128)) << 8) | ((bytes[index + 3] + 128)));
	 }
	 
	 
	 /**************************************************************************
                                 UTILITIES (DEBUG)
	 ***************************************************************************/
	 
	 /**
	  * Utility function for printing a range out of the {@link CircularFifoQueue} internalBuffer.
	  * @param start
	  * @param end
	  */
	 private void print_range(int start, int end){
		 log("Printing range [" + start + ", " + end + "]: ");
		 for(int i = start; i < end; i++){
			 log("\t[" + i + "]: " + internalBuffer.get(i));
		 }
	 }
	 
	 /**
	  * Utility function for printing arrays out in debug in a nice form.
	  * @param a - byte array to print
	  */
	 public static void print_array(byte[] a){
		 log("Printing out byte array: ");
		 if(a != null){
			 for(int i = 0; i < a.length; i++){
				 log("\t[" + i + "]: " + a[i]);
			 }
		 }else{
			 log("Byte array is null. skipping print");
		 }
	 }
 }