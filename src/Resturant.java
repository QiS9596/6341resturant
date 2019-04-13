import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
public class Resturant {
    public static void main(String[] args) {
//        System.out.println("Hello World!");
        Scanner input = new Scanner(System.in);
        String a = input.nextLine();
        String b= input.nextLine();
        String c = input.nextLine();
        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
        int num_diners = Integer.parseInt(a);
        int num_tables = Integer.parseInt(b);
        int num_cooks = Integer.parseInt(c);

        ExecutorService executor = Executors.newFixedThreadPool(num_cooks+num_diners+10);
    }
}

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
}
