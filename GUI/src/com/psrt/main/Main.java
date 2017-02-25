package com.psrt.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import com.psrt.containers.ImageHolder;
import com.psrt.entities.components.ImageComponent;
import com.psrt.entities.components.LabelComponent;
import com.psrt.entities.components.ProgressComponent;
import com.psrt.entities.components.TextAreaComponent;
import com.psrt.entities.systems.Bank;
import com.psrt.entities.systems.BankSystem;
import com.psrt.entities.systems.LogMonitor;
import com.psrt.entities.systems.TimingSystem;
import com.psrt.entities.systems.ValueSystem;
import com.psrt.guitabs.factories.ValueFactory;
import com.psrt.parsers.DictionaryParser;
import com.psrt.threads.SerialMonitor;
import com.psrt.threads.UIThread;

import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application{
	
	/*************************************
				PRIVATE FIELDS
	**************************************/	
	private static Main main;
	
    private com.artemis.World world;
    
    private UIThread uiThread;
    
    private SerialMonitor m;
    
    private ImageHolder imageHolder;
    
    private ValueFactory valueFactory;
    

    /*************************************
				PUBLIC FIELDS
 	**************************************/	
    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
   
    public static final boolean DEBUG = true; //Note, all Global static (public static) variables should be in caps
    
    public Bank bank;
    
    /**
     * As in all Java programs, the code starts here.
     * "launch(args);" Calls the JavaFX API and starts that magic.
     * @param args
     */
    public static void main(String[] args) {  //Note: main should go at the top of the class
    	LogMonitor.print("Starting");
        launch(args);
        LogMonitor.print("Clean exit");
    }
    
	/**
     * Called by the JavaFX API after it's done with its magic.
     * All I've got here is a call to the "Main" constructor and, after that, a call to a method which starts
     * initializing the UI.
     */
    @Override
    public void start(Stage primaryStage) {
        Main.main = this;
        this.initAll(primaryStage);
    }
    
    /**
     * <p>Initializes all the special stuff.  Here are the things that are initialized here:</p>
     * <p><ul>
     * <li>-{@link DictionaryParser}
     * <li>-{@link Bank}
     * <li>-{@link ImageHolder}
     * <li>-{@link ValueFactory}
     * </ul></p>
     * 
     * <p>Some other method calls for loading specific sections of code are also here:</p>
     * <p>loadResources(), initConfig(), initUIThread(), initSerialMonitor(), startThreads()</p>
     * @param primaryStage - {@link Stage} that the GUI is built on.
     */
    private void initAll(Stage primaryStage){
    	String user_dir = System.getProperty("user.dir");
    	LogMonitor.print(user_dir);
    	
    	DictionaryParser parser = null;
		try {
			parser = new DictionaryParser("res/dictionary.csv");
		} catch (FileNotFoundException | URISyntaxException e) {
			LogMonitor.print("Wow, couldn't open the dictionary file :/");
			e.printStackTrace();
		}
    	if(parser != null ){
    		parser.parseDictionary();
    		bank = new Bank(parser);
    		//TODO verify parser is working
    	}
    	else {
    		try {
				bank = new Bank();
			} catch (FileNotFoundException | URISyntaxException e) {
				e.printStackTrace();
			}
    	}
    	imageHolder = new ImageHolder();
    	loadResources();
    	initConfig();
    	valueFactory = new ValueFactory(world);
    	initUIThread(primaryStage);
    	initSerialMonitor();
    	startThreads();
    	
    	LogMonitor.lm = new LogMonitor(world);
    	
    	try {
			parser.close();
		} catch (IOException e) {
			LogMonitor.print("Problem closing DictionaryParser FileStream");
			e.printStackTrace();
		}
    }
    
    /**
     * Loads images into RAM (probably not very efficient, but it is faster than reading from disk) for access
     * by the GUI elements.  Uses a hashmap structure {@link ImageHolder} for storage, placement, and retrieval of said images.
     */
    private void loadResources(){
    	imageHolder.loadImage("res/images/battery_images/Green Light Medium.png", "green_light");
    	imageHolder.loadImage("res/images/battery_images/Red Light Medium.png", "red_light");
    	imageHolder.loadImage("res/images/battery_images/Yellow Light Medium.png", "yellow_light");
    	imageHolder.loadImage("res/images/battery_images/Off Light Medium.png", "off_light");
    	imageHolder.loadImage("res/images/battery_images/relay on.png", "relay_on");
    	imageHolder.loadImage("res/images/battery_images/relay off.png", "relay_off");
    }
    
    /**
     * Initializes the Entity system configurations. The entity system is responsible for the passing and manipulation of values around the program
     */
    private void initConfig() {
    	WorldConfiguration config = new WorldConfiguration();
    	config.setSystem(new BankSystem(bank));
    	config.setSystem(new ValueSystem(this));
    	config.setSystem(new TimingSystem());
    	//add systems here...
    	world = new com.artemis.World(config);
	}
    
    /**
     * This initializes the UI thread but doesn't start running it (updating it). That's in {@link startThreads()}
     * @param primaryStage
     */
    private void initUIThread(Stage primaryStage){
    	uiThread = new UIThread(primaryStage, world, this);
    }
    
    /**
     * Initialize {@link SerialMonitor}
     */
    private void initSerialMonitor() {
    	m = new SerialMonitor(bank);
	}

    /**
     * Starts everything up, really. Probably starting the UI and backend (serial) threads. Who knows
     */
    private void startThreads() {
		scheduler.scheduleAtFixedRate(uiThread, 0, 15, TimeUnit.MILLISECONDS);
		scheduler.scheduleAtFixedRate(new Thread("Test data"){
			long ticks = 0;
			ComponentMapper<LabelComponent> tm;
			ComponentMapper<ProgressComponent> pm;
			ComponentMapper<ImageComponent> im;
			ComponentMapper<TextAreaComponent> tam;
			
			@Override
			public void run(){
				//LogMonitor.print("Run");
				ValueSystem v = world.getSystem(ValueSystem.class);
				EntitySubscription sub = v.getSubscription();
				if(ticks == 0){
					tm = world.getMapper(LabelComponent.class);
					pm = world.getMapper(ProgressComponent.class);
					im = world.getMapper(ImageComponent.class);
					tam = world.getMapper(TextAreaComponent.class);
				}
				else{
					IntBag b = sub.getEntities();
					for(int i = 0; i < b.size(); i++){
						int id = b.get(i);
						LabelComponent tc = tm.getSafe(id);
						ProgressComponent pc = pm.getSafe(id);
						ImageComponent ic = im.getSafe(id);
						TextAreaComponent tac = tam.getSafe(id);
						
						LogMonitor.log("Test data interval", LogMonitor.LogType.UITHREAD);
						
						LogMonitor.log("Test data interval", LogMonitor.LogType.UITHREAD);
						
						if(tc != null){
							if(tc.getReference().equals("speed_display")){
								//LogMonitor.print("True");
								tc.setValue(""+ticks);
							}if(tc.getReference().equals("battery_1_voltage")){
								//tc.setValue(""+(50 - ticks));
							}if(tc.getReference().equals("battery_2_voltage")){
								//tc.setValue(""+ticks);
							}
							if(tc.getReference().equals("SOC_Label")){
								//tc.setValue(ticks * 2 + "%");
							}
							if(tc.getReference().equals("test_label")){
								tc.setValue(""+ ticks * 2);
							}
							
							
						}else if(pc != null){
							if(pc.getReference().equals("soc_indicator")){
								//pc.setValue((double) ticks / 50);
							}
							if(pc.getReference().equals("test_bar")){
								pc.setValue((double) ticks / 50);
							}
						}else if(ic != null){
							if(ic.getReference().equals("x6F7_u8_0_x08")){
								//ic.setValue(0);
								if(ic.getValue().intValue() == 0){
									
									ic.setValue(1);
								}else{
									ic.setValue(0);
								}
							}
						} else if (tac != null) {
							if (tac.getReference().equals("txt_area_errors")) {
								// System.out.println("text area errors called.\n");
								// tac.setValue("Hello\n");
							}
						} 
						
					}
				}
				if(ticks > 50) ticks = 0;
				ticks++;
			}
		}, 0, 1000, TimeUnit.MILLISECONDS);
    	//scheduler.scheduleAtFixedRate(entityThread, 30, 30, TimeUnit.MILLISECONDS);
	}

	/**
	 * Send entity data from anywhere else to the UI thread.
	 * @param e
	 */
    public synchronized void sendToUI(Entity e){
    	try {
    		if(uiThread != null)
    			uiThread.inject(e);
    		else
    			LogMonitor.print("Main: UIThread not initialized. Can't pass data from " + e.toString());
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
    }
  
    /**
     * Called when the GUI is closed.  Right now it is used to close the ThreadPoolExecutor which is running different threads. 
     * It also closes down the serial monitor and ports.
     */
    @Override
    public void stop(){
    	this.scheduler.shutdown();
    	try {
			this.scheduler.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} // wait for 10s in this case
    	this.scheduler.shutdownNow();
    	synchronized(SerialMonitor.lock){
    		SerialMonitor.lock.notify();
    	}
    	m.close();
    }
    
    
    /***************************************************************************
                                       GETTERS
     ***************************************************************************/
    
    /**
     * 
     * @return {@link ImageHolder} (of which there should only be one instance)
     */
    public ImageHolder getImages(){
    	return this.imageHolder;
    }
    
    /**
     * 
     * @return The {@link ValueFactory} (of which there should only be one instance)
     */
    public ValueFactory getValueFactory(){
    	return this.valueFactory;
    }
    
	/**
     * Get this class, which there should only be one instance of in existence.
     * @return the Main object
     */
	public synchronized static Main getMain(){
    	return main;
    }
	
	/**
	 * @return the Entity system's controller, {@link com.artemis.World} 
	 */
	public synchronized World getWorld(){
		return world;
	}
}
