/**
 * @author Bardski Grzegorz S20198
 */

package zad1;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChatServer extends Thread {
    private String chatServerHost;
    private int chatServerPort;
    private ServerSocketChannel chatServerChannel = null;
    private Selector chatServerSelector = null;
    private List<String> serverLog;

    private final Lock serverLock = new ReentrantLock();

    private Map<SocketChannel, String> chatServerClients;

    /*
     * Wymagania konstrukcyjne:
     * - użycie selektora
     * - serwer może obsługiwać równolegle wielu klientów,
     * - ale obsługa żądań klientów odbywa się w jednym wątku
     */
    public ChatServer(String host, int port) {
        this.chatServerHost = host;
        this.chatServerPort = port;
        serverLog = new ArrayList<>();
        chatServerClients = new HashMap<>();
    }

    // Metoda obsługująca uruchamianie serwera w osobnym wątku
    public void startServer() {
        try {
            /* Parametry gniazda serwera:
             * otwarcie
             * gniazdo nieblokujące
             * związanie z adresem i portem
             * otworzenie selektora
             * zarejestrowanie kanału do obsługi przez selektor
             */
            // Utworzenie kanału dla gniazda serwera
            chatServerChannel = ServerSocketChannel.open();

            // Tryb nieblokujący
            chatServerChannel.configureBlocking(false);

            // Powiązanie kanału z gniazdem
            chatServerChannel.socket().bind(new InetSocketAddress(chatServerHost, chatServerPort));

            // Utworzenie selektora
            chatServerSelector = Selector.open();
            chatServerChannel.register(chatServerSelector, SelectionKey.OP_ACCEPT);

            // Uruchomienie wątku
            this.start();
            System.out.println("Server started");
        } catch (Exception e) {
            saveException(e);
        }
    }

    // Metoda zatrzymująca serwer i wątek w którym działa
    public void stopServer() {
        try {
            serverLock.lock();
            this.interrupt();
            this.chatServerSelector.close();
            this.chatServerChannel.close();
            System.out.println("\nServer stopped");
        } catch (Exception e) {
            saveException(e);
        } finally {
            serverLock.unlock();
        }
    }

    //Metoda zwraca log serwera
    String getServerLog() {
        return String.join("\n", serverLog);
    }

    private void serveConnections() {
        while (!this.isInterrupted()) {
            try {
                chatServerSelector.select();

                if (isInterrupted()) break;

                Set<SelectionKey> serverSelectorKeys = chatServerSelector.selectedKeys();
                Iterator<SelectionKey> serverKeysIterator = serverSelectorKeys.iterator();

                while (serverKeysIterator.hasNext()) {
                    // Pętla wykonuje się dopóki w zestawie kluczy są jakieś
                    SelectionKey selectedKey = serverKeysIterator.next();
                    serverKeysIterator.remove();

                    if (selectedKey.isAcceptable()) {
                        // Jeśli jakiś klient chce się połączyć
                        SocketChannel clientSocketChannel = chatServerChannel.accept();
                        clientSocketChannel.configureBlocking(false);
                        clientSocketChannel.register(chatServerSelector, SelectionKey.OP_READ);
                        continue;
                    }
                    if (selectedKey.isReadable()) {
                        // Któryś z kanałów jest gotowy do czytania danych
                        SocketChannel clientSocketChannel = (SocketChannel) selectedKey.channel();
                        serveRequest(clientSocketChannel);
                    }
                }
            } catch (Exception exc) {
                saveException(exc);
            }
        }
    }

    // Metoda czytająca dane z kanału gniazda
    private String readSocketChannel(SocketChannel socketChannel) throws IOException {
        // Bufor bajtowy - do niego są wczytywane dane z kanału
        // Inicjalizacja wewnątrz metody pozwala uniknąć konieczności czyszczenia bufora
        ByteBuffer serverByteBuffer = ByteBuffer.allocate(1024);

        // Tu będzie zlecenie do pezetworzenia
        StringBuilder clientRequest = new StringBuilder();

        // Strona kodowa do kodowania/dekodowania buforów
        Charset charset = StandardCharsets.UTF_8;
        while (true) {
            // W ten sposób możemy załatwić problem dt. tego,
            // że nie wiemy ile jest bajtów odczytanych w jednej operacji 'read'
            int n = socketChannel.read(serverByteBuffer);
            if (n > 0) {
                serverByteBuffer.flip();
                CharBuffer cbuf = charset.decode(serverByteBuffer);
                while (cbuf.hasRemaining()) {
                    char c = cbuf.get();
                    if (c == '\n')
                        return clientRequest.toString();

                    clientRequest.append(c);
                }
            }
        }
    }

    private static final int BSIZE = 256;

    // Bufor na wczytywanie danych z kanału
    private ByteBuffer serverByteBuffer = ByteBuffer.allocate(BSIZE);

    // Bufor na zapytanie od klientów
    private StringBuffer chatClientRequest = new StringBuffer();

    private static Charset charset = StandardCharsets.UTF_8;

    private void serveRequest(SocketChannel requestSocketChannel) throws Exception {
        // Obsługa odpowiedzi na zapytania klientów
        if (!requestSocketChannel.isOpen())
            return;

        chatClientRequest.setLength(0);
        serverByteBuffer.clear();

        try {
            readLoop:
            // Czytanie jest nieblokujące
            // kontynujemy je dopóki
            while (!this.isInterrupted()) {
                int n = requestSocketChannel.read(serverByteBuffer);
                // nie natrafimy na koniec wiersza

                if (n > 0) {
                    try {
                        serverLock.lock();
                        // Odwrócenie bufora
                        serverByteBuffer.flip();

                        // Dekodowanie wiadomości
                        CharBuffer cbuf = charset.decode(serverByteBuffer);

                        while (cbuf.hasRemaining()) {
                            char c = cbuf.get();
                            if (c == '\n') break readLoop;
                            chatClientRequest.append(c);
                        }
                    } finally {
                        serverLock.unlock();
                    }
                }

            }

            String[] request = chatClientRequest.toString().split(" +");

            // 1. Metoda
            String method = request[0];
            // 2. Id klienta
            String clientId = request[1];

            switch (method) {
                case "IN": {
                    /* Komunikat do logowania ma format: IN [clientID]. np: IN 2
                     * Gdzie "2" to id klienta.
                     */
                    chatServerClients.put(requestSocketChannel, clientId);
                    String log = clientId + " logged in";
                    broadcastMessages(log);
                    logMessage(log);
                    break;
                }
                case "SAY": {
                    /*
                     * Komunikat z tekstem powinien mieć strukturę: SAY [clientID] [wiadomość].
                     * np.: SAY 1 Dzień dobry
                     * albo SAY 3 Do widzenia
                     */
                    String message = extractRequest(request);
                    String log = clientId + ": " + message;
                    broadcastMessages(log);
                    logMessage(log);
                    break;
                }
                case "OUT": {
                    /*
                     * Komunikat do logowania ma format: IN [clientID]. np: IN 2
                     * Gdzie "2" to id klienta.
                     */
                    String log = clientId + " logged out";
                    broadcastMessages(log);
                    logMessage(log);
                    requestSocketChannel.close();
                    chatServerClients.remove(requestSocketChannel);
                    break;
                }
            }
        } catch (Exception ex) {
            saveException(ex);
        }
    }

    private String extractRequest(String[] requestArray) {
        String[] sliced = new String[requestArray.length - 2];
        System.arraycopy(requestArray, 2, sliced, 0, sliced.length);
        StringBuilder result = new StringBuilder(new StringBuilder());
        for (String s : sliced) {
            result.append(s).append(" ");
        }
        return result.toString();
    }

    @Override
    public void run() {
        // Obsługa zapytań klientów.
        serveConnections();
    }

    // Metoda rozsyłająca otrzymaną wiadomość do podłączonych klientów
    private void broadcastMessages(String message) throws IOException {

        ByteBuffer byteBuffer = charset.encode(message);
        for (Map.Entry<SocketChannel, String> client : chatServerClients.entrySet()) {
            SocketChannel clientSocketChannel = client.getKey();

            clientSocketChannel.write(byteBuffer);
        }
    }

    private void logMessage(String toLog) {
        serverLog.add(LocalTime.now() + " " + toLog);
    }

    private void saveException(Exception e){
        serverLog.add("*** " + e);
    }
}

