package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler {
    DataInputStream in;
    DataOutputStream out;
    Server server;
    Socket socket;

    private String nickname;
    private String login;

    private final int timeOut = 5000;
    private final int timeDisc = 2000;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream( socket.getInputStream() );
            out = new DataOutputStream( socket.getOutputStream() );
            System.out.println( "Client connected " + socket.getRemoteSocketAddress() );

            new Thread( () -> {
                try {
                    //цикл аутентификации
                    socket.setSoTimeout( timeOut - timeDisc );
                    sendMsg( "Вам неоходимо авторизоваться в течении " + (timeOut/100) +
                            " секунд" );
                    cycleOfAuthentications();

                    //цикл работы
                    cycleOfWorks();

                    //SocketTimeoutException

                } catch (SocketTimeoutException e) {
                    sendMsg( "Client disconnected by time" );
                    try {
                        Thread.sleep(timeDisc);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }

                    System.out.println( "Client disconnected by time" + socket.getRemoteSocketAddress() );
                    closeThis();
                } catch (IOException e) {
                    e.printStackTrace();

                } finally {
                    server.unsubscribe( this );
                    System.out.println( "Client disconnected " + socket.getRemoteSocketAddress() );
                    closeThis();
                }
            } ).start();
            new Thread( () -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                server.broadcastClientList();
            }).start();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cycleOfAuthentications() throws IOException {
        while (true) {

            String str = in.readUTF();

            if (str.startsWith( "/reg " )) {
                String[] token = str.split( "\\s" );
                if (token.length < 4) {
                    continue;
                }
                boolean b = server.getAuthService()
                        .registration( token[1], token[2], token[3] );
                if (b) {
                    sendMsg( "/regok" );
                } else {
                    sendMsg( "/regno" );
                }
            }

            if (str.startsWith( "/auth " )) {
                String[] token = str.split( "\\s" );
                if (token.length < 3) {
                    continue;
                }
                String newNick = server.getAuthService()
                        .getNicknameByLoginAndPassword( token[1], token[2] );
                if (newNick != null) {
                    login = token[1];
                    if (!server.isLoginAuthenticated( login )) {
                        nickname = newNick;
                        sendMsg( "/authok " + newNick );
                        socket.setSoTimeout( 0 ); //После успешной авторизации отключаем таймер
                        server.subscribe( this );
                        break;
                    } else {
                        sendMsg( "С этим логином уже вошли в чат" );
                    }

                } else {
                    sendMsg( "Неверный логин / пароль" );
                }
            }

        }
    }

    private void cycleOfWorks() throws IOException {
        while (true) {
            String str = in.readUTF();
            if (str.startsWith( "/" )) {
                if (str.equals( "/end" )) {
                    sendMsg( "/end" );
                    break;
                }
                if (str.startsWith( "/w " )) {
                    String[] token = str.split( "\\s", 3 );
                    if (token.length < 3) {
                        continue;
                    }
                    server.privateMsg( this, token[1], token[2] );
                }
            } else {
                server.broadcastMsg( this, str );
            }
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF( msg );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeThis() {
        try {
            socket.close();
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname() {
        return nickname;
    }

    public String getLogin() {
        return login;
    }

}
