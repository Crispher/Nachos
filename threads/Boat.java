package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	private static final int ADULT_AMOUNT = 30;
	private static final int CHILD_AMOUNT = 30;
    static BoatGrader bg;
    
    private static Lock mutex;

	public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
	System.out.println("\n ***Testing Boats with " + ADULT_AMOUNT + " adults and " + CHILD_AMOUNT + " children***");
	begin(ADULT_AMOUNT, CHILD_AMOUNT, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;
	mutex = new Lock();
	ChildWaitOahu = new Condition(mutex);
	AdultWaitOahu = new Condition(mutex);
	ChildWaitMolokai = new Condition(mutex);
	oahu = new Island();
	molokai = new Island();
	finished = new Semaphore(0);
	// Instantiate global variables here
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.

	/*Runnable r = new Runnable() {
	    public void run() {
                SampleItinerary();
            }
        };
        KThread t = new KThread(r);
        t.setName("Sample Boat Thread");
        t.fork();*/
	
		oahu.hasBoat = true;
		molokai.hasBoat = false;
		Runnable r_child = new Runnable(){
	    public void run() {
                ChildItinerary();
            }
        };
		Runnable r_adult = new Runnable(){
		    public void run() {
	                AdultItinerary();
	            }
		};
		//mutex.acquire();
		for (int i = 0; i < children; ++ i) {
			KThread t = new KThread(r_child);
			t.setName("Child No. " + i);
			t.fork();
		}//mutex.release();
		for (int i = 0; i < adults; ++ i) {
			KThread t = new KThread(r_adult);
			t.setName("Adult No. " + i);
			t.fork();
		}
		finished.P();
		System.out.println("\n***All work done!***");
    }

    static void Row(int type, int direction, int role)
    {
    	if (direction == MOLOKAI)
    	{
    		molokai.hasBoat = true;
    		oahu.hasBoat = false;
    		if (type == ADULT)
    		{
    			bg.AdultRowToMolokai();
    			oahu.count[ADULT] --;
    			molokai.count[ADULT] ++;
    		}
    		else if (type == CHILD)
    		{
    			if (role == PILOT)
    				bg.ChildRowToMolokai();
    			else if (role == PASSENGER)
    				bg.ChildRideToMolokai();
    			else System.out.println("\n ***Error in role assignment.***");
    			oahu.count[CHILD] --;
    			molokai.count[CHILD] ++;			
    		}
    		else System.out.println("\n ***Error in type information.***");
    		ChildWaitMolokai.wakeAll();
    	}
    	else if (direction == OAHU)
    	{
    		molokai.hasBoat = false;
    		oahu.hasBoat = true;
    		if (type == ADULT)
    		{
    			bg.AdultRowToOahu();
    			oahu.count[ADULT] ++;
    			molokai.count[ADULT] --;
    		}
    		else if (type == CHILD)
    		{
    			if (role == PILOT)
    				bg.ChildRowToOahu();
    			else if (role == PASSENGER)
    				bg.ChildRideToOahu();
    			else System.out.println("\n ***Error in role assignment.***");
    			oahu.count[CHILD] ++;
    			molokai.count[CHILD] --;		
    		}
    		else System.out.println("\n ***Error in type information.***");
    		ChildWaitOahu.wakeAll();
    		AdultWaitOahu.wakeAll();
    	}
    	else System.out.println("\n ***Error in boat travel direction.***");
    }
    
    static void AdultItinerary()
    {
	bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
	oahu.count[ADULT] ++;
	mutex.acquire();
	while (!((oahu.count[CHILD] <= 1) && (oahu.hasBoat == true) && (childWaitRide == 0)))
	{
		if (oahu.hasBoat == true) ChildWaitOahu.wakeAll();
		AdultWaitOahu.sleep();
	}
	Row(ADULT, MOLOKAI, PILOT);
	mutex.release();
    }

    static void ChildItinerary()
    {
	bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE.
	oahu.count[CHILD] ++;
	int loc = OAHU;
	for (;;)
	{
		mutex.acquire();
		while (!Started()) ChildWaitOahu.sleep();
		if (loc == OAHU)
		{
			if (childWaitRide > 0)
			{
				Row(CHILD, MOLOKAI, PASSENGER);
				childWaitRide --;
				loc = MOLOKAI;
				mutex.release();
				continue;
			}
			
			if (oahu.hasBoat == true)
			{
					if (oahu.count[CHILD] >= 2)
					{
						childWaitRide ++;
						ChildWaitOahu.wake();
						Row(CHILD, MOLOKAI, PILOT);
						loc = MOLOKAI;
					}
					else if (oahu.count[ADULT] == 0)
					{
						Row(CHILD, MOLOKAI, PILOT);
						loc = MOLOKAI;
					}
					else
					{
						AdultWaitOahu.wake();
						ChildWaitOahu.sleep();
					}
			}
			else ChildWaitOahu.sleep();
		}
		else if (loc == 	MOLOKAI)
		{
			if (Finished())
			{
				mutex.release();
				break;
			}
			else
			{
				if ((molokai.hasBoat == true) && (childWaitRide == 0))
				{
					Row(CHILD, OAHU, PILOT);
					loc = OAHU;
				}
				else 
				{
					ChildWaitOahu.wake();
					ChildWaitMolokai.sleep();
				}
			}
		}
		else System.out.println("\n ***Error in child location.***");
		mutex.release();
	} // end for
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
    private static  Condition ChildWaitOahu, AdultWaitOahu, ChildWaitMolokai;
    private static class Island {
    	int[] count = new int[2];
    	boolean hasBoat;
    }
    private static int childWaitRide = 0;
    private static boolean Finished() {
    	if (molokai.count[ADULT] == ADULT_AMOUNT && molokai.count[CHILD] == CHILD_AMOUNT)
    		{finished.V();return true;}
    	else return false;
    }
    private static boolean Started() {
    	if (molokai.count[ADULT] + molokai.count[CHILD] + oahu.count[ADULT] + oahu.count[CHILD] == ADULT_AMOUNT + CHILD_AMOUNT)
    		return true;
    	else return false;
    }
    private static Island oahu, molokai; 
    private static Semaphore finished;
    private static final int ADULT = 0;
    private static final int CHILD = 1;
    private static final int MOLOKAI = 2;
    private static final int OAHU = 3;
    private static final int PILOT = 4;
    private static final int PASSENGER = 5;
    //}
}
