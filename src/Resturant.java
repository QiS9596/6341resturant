import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.Scanner;
public class Resturant {
    public static void main(String[] args) {
        //read input
        Scanner input = new Scanner(System.in);
        String a = input.nextLine().replaceAll(" |\n","");
        String b= input.nextLine().replaceAll(" |\n","");
        String c = input.nextLine().replaceAll(" |\n","");
        int num_diners = Integer.parseInt(a);
        int num_tables = Integer.parseInt(b);
        int num_cooks = Integer.parseInt(c);
        //executor_cust is responsible for holding all customer threads
        ExecutorService executor_cust = Executors.newFixedThreadPool(num_cooks+num_diners+10);
        //executor_rest is responsible for holding all resturant threads
        ExecutorService executor_rest = Executors.newFixedThreadPool(num_cooks+num_diners+10);

        resource_controller rc = new resource_controller(num_tables);
        GlobalTimerThread GT = new GlobalTimerThread();
        OrderManager om = new OrderManager();

        for(int i = 0; i < num_cooks; i++){
            executor_rest.execute(new Cook(om, rc, GT, i,num_diners));
        }

        for(int i = 0; i < num_diners; i++){
            String a_cust = input.nextLine();

            String[] a_cust_info = a_cust.split(" ");

            Order this_order = new Order();
            this_order.burger_order = Integer.parseInt(a_cust_info[1]);
            this_order.fries_order = Integer.parseInt(a_cust_info[2]);
            this_order.coke_order = Integer.parseInt(a_cust_info[3]);
            this_order.fulfilled = 0;
            this_order.assigned = 0;
            this_order.id = i;
            executor_cust.execute(new Customer(rc, Integer.parseInt(a_cust_info[0]),this_order, om, GT));
        }
        executor_rest.execute(GT);
        try{}catch (Exception e){}
        executor_cust.shutdown();
        int times = 0;
        while (!executor_cust.isTerminated()){
            if(times >= 100000000){
//            System.out.println(((ThreadPoolExecutor)executor_cust).getActiveCount());
            times = 0;
            }
            times ++;
        }
        System.out.println("All customer finished meal @ time "+ Customer.last_time);
        GlobalTimerThread.stoprunning();

        om.stoprunning();
        executor_rest.shutdown();
        return;
    }
}
/**
 * Design pipeline
 * resource_controller: holding the tables and the machines
 * ordermanager: handling and assigning orders
 * globaltimer: global timer
 * customer: customer object
 * cook: cook object
 * the customer first enter the resturant, first ask resource_controller for a table, after granted a table, it pass it
 * order to th ordermanager. after the order is fulfilled, it'll count down 30min and then terminate
 *
 * the cook will keep asking the order manager for an order, after acquire the order, it will ask the resource_controller
 * for the machine, then it'll ask ordermanager to fulfill the order
 *
 * after all customer finishes, terminate all other threads
 * */


/**
 * resources_controller class
 * only one object is created
 * responsible for taking control of table and machines
 * */
class resource_controller{
    private static Lock tbl_lock = new ReentrantLock();
    private static Condition tbl_cd = tbl_lock.newCondition();
    private int table_num;
    private int [] tables;
    private int max_tables;

    private static Lock burger_machine = new ReentrantLock();
    private Boolean burger_machine_avail = true;
    private static Lock fries_machine = new ReentrantLock();
    private Boolean fries_machine_avil = true;
    private static Lock coke_machine = new ReentrantLock();
    private Boolean coke_machine_avil = true;
    private static Condition burger_machine_cd = burger_machine.newCondition();
    private static Condition fires_machine_cd = fries_machine.newCondition();
    private static Condition coke_machine_cd = coke_machine.newCondition();
    public resource_controller(int table_num){
        this.table_num = table_num;
        this.max_tables = table_num;
        tables = new int[this.table_num];
        for(int i = 0; i < tables.length; i++){
            tables[i] = 1;
        }
    }
    public int aquire_a_table(){
        tbl_lock.lock();
        if (this.table_num == 0){
            try{
                tbl_cd.await();
            }catch(InterruptedException e){
            }
        }
        this.table_num -= 1;

        int tbl_idx = -1;
        for (int i = 0; i<this.tables.length; i++){
            if(tables[i] == 1){
                tables[i] = 0;
                tbl_idx = i;
                break;
            }
        }
        tbl_lock.unlock();
        return tbl_idx;
    }
    public void release_a_table(int released_tbl_idx){
        tbl_lock.lock();
        this.tables[released_tbl_idx] = 1;
        this.table_num += 1;
        if(this.table_num == 1){
            tbl_cd.signal();
        }
        tbl_lock.unlock();
    }
    public boolean aquire_burger_machine(){

        boolean result = false;
        burger_machine.lock();

        if (burger_machine_avail){
            burger_machine_avail = false;
            result = true;
        }
        burger_machine.unlock();

        return result;
    }
    public void release_burger_machine(){
        burger_machine.lock();
        burger_machine_avail = true;
        burger_machine.unlock();
    }
    public boolean aquire_fries_machine(){
        boolean result = false;
        fries_machine.lock();
        if(fries_machine_avil){
            fries_machine_avil = false;
            result = true;
        }
        fries_machine.unlock();
        return result;
    }
    public void release_fries_machine(){

        fries_machine.lock();
        fries_machine_avil = true;
        fries_machine.unlock();
//        System.out.println("fries machine released");
    }
    public boolean aquire_coke_machine(){
        boolean result = false;
        coke_machine.lock();
        if(coke_machine_avil){
            coke_machine_avil = false;
            result = true;
        }
        coke_machine.unlock();
        return result;
    }
    public void release_coke_machine(){
        coke_machine.lock();
        coke_machine_avil = true;
        coke_machine.unlock();
    }
}

class Customer extends Thread{
    public static int last_time = 0;
    public static Lock lockofTime = new ReentrantLock();
    public Order my_order;
    public int entering_time;
    private OrderManager om;
    private GlobalTimerThread gt;
    public int table = -1;
    public resource_controller rc;
    public Customer(resource_controller rc, int entering_time, Order input_order, OrderManager om, GlobalTimerThread gt){
        this.rc = rc;
        this.my_order = input_order;
        this.entering_time = entering_time;
        this.om = om;
        this.gt = gt;
    }
    public void run(){
//        System.out.println("customer object created");
        gt.waitForTime(this.entering_time);
        int table = rc.aquire_a_table();
        System.out.println("Customer: " + this.my_order.id + " seated at table "+ table + " @ time:"+gt.getTime());
        om.addOrder(this.my_order);
        System.out.println("Customer: " + this.my_order.id + ": got their food, @ time:"+gt.getTime());
        gt.waitForTime(gt.getTime() + 30);
        int left_time = gt.getTime();
        lockofTime.lock();
        last_time = left_time;
        lockofTime.unlock();
        rc.release_a_table(table);
    }
}

class Order {
    public int burger_order;
    public int fries_order;
    public int coke_order;
    public int id;
    public int fulfilled = 0;
    public int assigned = 0;
}

class OrderManager{
    public static Lock order_manager_lk = new ReentrantLock();
    public static Condition order_manager_cd = order_manager_lk.newCondition();
    private static Lock stop_lock = new ReentrantLock();
    private static Boolean stop = false;
    public ArrayList<Order> orders;
    private int avil_order = 0;
    public OrderManager(){
        orders = new ArrayList<>();
    }
    public void addOrder(Order my_order){

        order_manager_lk.lock();
        orders.add(my_order);
        avil_order += 1;
        order_manager_cd.signalAll();
        int index = orders.size()-1;
        while(orders.get(index).fulfilled == 0){
            try{
            order_manager_cd.await();}catch(InterruptedException e){}
        }
        order_manager_lk.unlock();
    }

    public void stoprunning(){
        stop_lock.lock();
        stop = true;
        stop_lock.unlock();
        order_manager_lk.lock();
        order_manager_cd.signalAll();
        order_manager_lk.unlock();
    }

    public int assignOrder(){
        order_manager_lk.lock();
        while (avil_order <=0){
            try{
            order_manager_cd.await();
            stop_lock.lock();
            if(stop){
                stop_lock.unlock();
                order_manager_lk.unlock();
                return -1;
            }
            stop_lock.unlock();
            }catch (InterruptedException e){}
        }
        avil_order -= 1;
        int result = 0;

        for (int index = 0; index < orders.size();index++)
        {
            if(orders.get(index).assigned == 0){
                result = index;
                orders.get(index).assigned = 1;
                break;
            }
        }
        order_manager_lk.unlock();
        return result;
    }
    public Order getOrderObj(int orderNum){
        Order tar_order;
        order_manager_lk.lock();
        tar_order = orders.get(orderNum);
        order_manager_lk.unlock();
        return tar_order;
    }
    public void fulfillOrder(int orderNum){
        order_manager_lk.lock();
        orders.get(orderNum).fulfilled = 1;
        order_manager_cd.signalAll();
        order_manager_cd.signalAll();
        order_manager_lk.unlock();
    }
}

class Cook extends Thread{
    private OrderManager om;
    private resource_controller rc;
    private GlobalTimerThread gt;
    private int id;
    private int customer_num;
    public Cook(OrderManager om, resource_controller rc, GlobalTimerThread gt, int id, int customer_num){
        this.om = om;
        this.gt = gt;
        this.rc = rc;
        this.id = id;
        this.customer_num = customer_num;
    }
    public void run(){
        for (int times = 0; times < 2*customer_num; times++){
            int currentOrder = om.assignOrder();
            if(currentOrder < 0){
                return;
            }
            Order myorder = om.getOrderObj(currentOrder);
            System.out.println("Cook: " + id + " assigned order of Customer: " + myorder.id + ", @ time "+gt.getTime());

            int burger_completed = 0;
            int fries_completed = 0;
            int coke_completed = 0;
            //as long as the order is not totally fulfilled, keep doing this loop
            while(burger_completed < myorder.burger_order
                || fries_completed < myorder.fries_order
                || coke_completed < myorder.coke_order){
                //if more burger needed, try grab the burger machine
                if(burger_completed< myorder.burger_order){
                    //if the burgermachine is granted
                    if(rc.aquire_burger_machine()){
                        System.out.println("Cook: " + id + " using burger machine to work on the order of Customer "+ myorder.id+ ", @ time "+gt.getTime());
                        //fullfil all the burger order in this order
                        while(burger_completed < myorder.burger_order){
                            int a = gt.getTime()+5;

                            gt.waitForTime(a);
                            burger_completed += 1;
                        }
                        rc.release_burger_machine();
                    }
                    //if the burgermachine is not assigned, try others
                }
                if(fries_completed < myorder.fries_order){
                    if(rc.aquire_fries_machine()){
                        System.out.println("Cook: "+ id + " using fries machine to work on the order of Customer " + myorder.id+ ", @ time "+gt.getTime());
                        while(fries_completed < myorder.fries_order){
                            gt.waitForTime(gt.getTime()+3);
                            fries_completed += 1;
                        }
                        rc.release_fries_machine();
                    }
                }
                if(coke_completed< myorder.coke_order){
                    if(rc.aquire_coke_machine()){
                        System.out.println("Cook: " + id + " using coke machine to work on the order of Customer " + myorder.id+ ", @ time "+gt.getTime());
                        while(coke_completed < myorder.coke_order){
                            gt.waitForTime(gt.getTime() + 1);
                            coke_completed += 1;
                        }
                        rc.release_coke_machine();
                    }
                }
//                try{
//                    sleep(1000);
//                }catch (InterruptedException e){}
            }
            om.fulfillOrder(currentOrder);
        }
    }
}

class GlobalTimerThread extends Thread{
    private int time = 0;
    public static Lock timer_lk = new ReentrantLock();
    public static Condition timer_cd = timer_lk.newCondition();
    private static Boolean stop = false;
    private static Lock stop_lk = new ReentrantLock();
    public void run(){
        while(time < 500){
            try{sleep(100);}catch (InterruptedException e){}
            timer_lk.lock();

            time = time +1;
            timer_cd.signalAll();
            timer_lk.unlock();
            stop_lk.lock();
            if(stop){
                stop_lk.unlock();
                break;
            }
            stop_lk.unlock();
        }
    }
    public void waitForTime(int targetTime){

        timer_lk.lock();
        while(targetTime > this.time){
            try{
                timer_cd.await();
            }catch (InterruptedException e){}
        }
        timer_lk.unlock();
    }
    public  int getTime(){
        int result;
        timer_lk.lock();
        result = this.time;
        timer_lk.unlock();
        return result;
    }
    public static void stoprunning(){
        stop_lk.lock();
        stop = true;
        stop_lk.unlock();
    }
}