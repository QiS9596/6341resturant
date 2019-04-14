import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
public class Resturant {
    public static void main(String[] args) {

//        Scanner input = new Scanner(System.in);
//        String a = input.nextLine();
//        String b= input.nextLine();
//        String c = input.nextLine();
//        System.out.println(a);
//        System.out.println(b);
//        System.out.println(c);
//        int num_diners = Integer.parseInt(a);
//        int num_tables = Integer.parseInt(b);
//        int num_cooks = Integer.parseInt(c);

//        ExecutorService executor = Executors.newFixedThreadPool(num_cooks+num_diners+10);
        System.out.println("hello,world");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        resource_controller rc = new resource_controller(2);
        GlobalTimerThread GT = new GlobalTimerThread();
        executor.shutdown();
        while (!executor.isTerminated()){

        }

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
    private static Lock fries_machine = new ReentrantLock();
    private static Lock coke_machine = new ReentrantLock();
    private static Condition burger_machine_cd = burger_machine.newCondition();
    private static Condition fires_machine_cd = fries_machine.newCondition();
    private static Condition coke_machine_cd = coke_machine.newCondition();
    public resource_controller(int table_num){
        this.table_num = table_num;
        this.max_tables = table_num;
        tables = new int[this.table_num];
        for(int i = 0; i < tables.length; i++){
            tables[1] = 1;
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
        int tbl_idx = 0;
        for (int i = 0; i<this.tables.length; i++){
            if(tables[i] == 1){
                tables[i] = 0;
                tbl_idx = i;
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
    public void aquire_burger_machine(){
        burger_machine.lock();
    }
    public void release_burger_machine(){
        burger_machine.unlock();
    }
    public void aquire_fries_machine(){
        fries_machine.lock();
    }
    public void release_fries_machine(){
        fries_machine.unlock();
    }
    public void aquire_coke_machine(){
        coke_machine.lock();
    }
    public void release_coke_machine(){
        coke_machine.unlock();
    }
}

class Customer extends Thread{
    public Order my_order;
    public int entering_time;
    public int table = -1;
    public resource_controller rc;
    public Customer(resource_controller rc, int entering_time, Order input_order){
        this.rc = rc;
        this.my_order = input_order;
        this.entering_time = entering_time;
    }
    public void run(){
        rc.aquire_a_table();
        System.out.println("granted table");
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
    public ArrayList<Order> orders;
    private int avil_order = 0;
    public OrderManager(int maximum_cust){
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

    public int assignOrder(){
        order_manager_lk.lock();
        while (avil_order <=0){
            try{
            order_manager_cd.await();}catch (InterruptedException e){}
        }
        avil_order -= 1;
        int result = 0;
        for (int index = 0; index < orders.size();index++)
        {
            if(orders.get(index).assigned == 0){
                result = index;
            }
        }
        order_manager_lk.unlock();
        return result;
    }

}

class Cook extends Thread{
    private OrderManager om;
    public Cook(OrderManager om){
        this.om = om;
    }
    public void run(){
        while(true){
            int currentOrder = om.assignOrder();
            //TODO
        }
    }
}

class GlobalTimerThread extends Thread{
    private int time = 0;
    public static Lock timer_lk = new ReentrantLock();
    public static Condition timer_cd = timer_lk.newCondition();
    public void run(){
        while(time < 5000){
            timer_lk.lock();
            try{sleep(10);}catch (InterruptedException e){}
            time = time +1;
            timer_cd.signalAll();
            timer_lk.unlock();
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
}