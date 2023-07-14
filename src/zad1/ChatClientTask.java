/**
 *
 *  @author Bardski Grzegorz S20198
 *
 */

package zad1;


import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ChatClientTask extends FutureTask<ChatClient> {
    public ChatClientTask(Callable<ChatClient> callable) {
        super(callable);
    }

    public static ChatClientTask create(ChatClient c, List<String> msgs, int wait) {
        return new ChatClientTask(() -> {
            try {
                c.login();
                if (wait != 0) Thread.sleep(wait);

                for (String m : msgs) {
                    c.send(m);
                    if (wait != 0) Thread.sleep(wait);
                }
                c.logout();

                if (wait != 0) Thread.sleep(wait);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return c;
        });
    }

    public ChatClient getClient() {
        try {
            return this.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }
}

