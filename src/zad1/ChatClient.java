/**
 *
 *  @author Bardski Grzegorz S20198
 *
 */

package zad1;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChatClient extends Thread {
    private String chatClientHost;
    private int chatClientPort;
    private String chatId;

    private final Lock clientLock = new ReentrantLock();

    private SocketChannel clientSocketChannel = null;

    // Lista przechowująca logi dla każdego klienta w formacie String
    private List<String> clientLog;

    /**
     * Konstruktor - otwarcie kanału i połączenie z serwerem
     *
     * @param host
     * @param port
     * @param id
     */
    public ChatClient(String host, int port, String id) {
        this.chatClientHost = host;
        this.chatClientPort = port;
        this.chatId = id;

        clientLog = new ArrayList<>();
        clientLog.add("\n=== " + chatId + " chat view");

        try {
            clientSocketChannel = SocketChannel.open();
            clientSocketChannel.configureBlocking(false);
            clientSocketChannel.connect(new InetSocketAddress(host, port));

            while (!clientSocketChannel.finishConnect()) {
                try {
                    Thread.sleep(200);
                } catch (Exception ex) {
                    saveException(ex);
                }
            }
        } catch (IOException exc) {
            saveException(exc);
            System.exit(2);
        }

    }

    // Logowanie klienta na serwer
    public void login() {
        try {
            sendMessage("IN " + this.chatId);
            this.start();
        } catch (Exception ex) {
            saveException(ex);
        }
    }

    // Domyślna metoda wysyłania komunikatów
    public void send(String message) {
        try {
            sendMessage("SAY " + this.chatId + " " + message);
        } catch (Exception ex) {
            saveException(ex);
        }
    }

    //Wylogowanie klienta
    public void logout() {
        try {
            sendMessage("OUT " + this.chatId);

            // Zatrzymanie wątku
            clientLock.lock();
            this.interrupt();
            clientSocketChannel.close();
            clientSocketChannel.socket().close();
        } catch (IOException ioex) {
            saveException(ioex);
        } finally {
            clientLock.unlock();
        }
    }

    private static Charset charset = StandardCharsets.UTF_8;

    private static final int BUFFER_SIZE = 256;

    public void sendMessage(String request) throws IOException {
        // Do bufora znakowego ładujemy zapytanie
        CharBuffer requestCharBuffer = CharBuffer.wrap(request + "\n");

        // Utworzenie bufora na dane wychodzące
        // i umieszczenie tam zapytania
        ByteBuffer clientOutputBuffer = charset.encode(requestCharBuffer);

        // Zapisanie zawartości bufora do kanału
        clientSocketChannel.write(clientOutputBuffer);
    }


    public void run() {
        listen();
    }

    private void listen() {
        while (!this.isInterrupted()) {
            ByteBuffer clientInputBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            StringBuilder chatServerResponse = new StringBuilder();

            clientInputBuffer.clear();
            chatServerResponse.setLength(0);

            try {
                while (clientSocketChannel.read(clientInputBuffer) > 0) {
                    clientInputBuffer.flip();
                    CharBuffer responseCharBuffer = charset.decode(clientInputBuffer);
                    chatServerResponse.append(responseCharBuffer);
                    clientInputBuffer.clear();
                }
            } catch (Exception e) {
                saveException(e);
            }

            if (!chatServerResponse.toString().isEmpty()){
                clientLog.add(chatServerResponse.toString());
            }
        }
    }

    public String getChatView() {
        return String.join("\n", clientLog);
    }

    private void saveException(Exception e) {
        clientLog.add("*** " + e.toString());
    }
}
