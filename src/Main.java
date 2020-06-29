import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    final static int bufferSize = 32768;
    static byte[] b = new byte[bufferSize];
    static byte[] c = new byte[bufferSize];
    static InetAddress address;
    final static int timeout = 5000; //максимальное время ожидания ответа от сервера
    static int port;
    static boolean script = false;
    static String owner;


    public static void main(String[] args) throws Exception {
        address = InetAddress.getLocalHost();
        while (port < 1025 || port > 65000) {
            System.out.println("Введите номер порта(целое число, большее 1024, но меньшее 65000)");
            Scanner scanner = new Scanner(System.in);
            if(scanner.hasNext()) {
                if (scanner.hasNextInt()) {
                    port = scanner.nextInt();
                }
            }else {
                System.out.println("Не душите ctrl+d");
                System.exit(0);
            }
        }
        DatagramSocket datagramSocket = new DatagramSocket();//пытаемся отправить запрос на сервер
        DatagramPacket sent = new DatagramPacket(c, bufferSize, address, port);
        authorization(datagramSocket, sent);
        while (true) {
            Commands command;
            if (!script) {
                command = UsersInput.Input();
                if (command.getName().equals("exit")) {
                    System.exit(0);
                }
                send(command, datagramSocket, sent);
            }
            command = receive(datagramSocket);
            command = resultTreatment(command, datagramSocket, sent);
            System.out.println(command.getResult());
            System.out.println(" ");
        }
    }


    public static byte[] serialize(Commands command) throws IOException {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            try (ObjectOutputStream o = new ObjectOutputStream(b)) {
                o.writeObject(command);
            }
            return b.toByteArray();
        }
    }


    public static Commands creationTime(Commands command, DatagramSocket datagramSocket, DatagramPacket datagramPacket) throws Exception {
        if (command.getResult() != null && command.getResult().equals("It's creation time!")) {
            command.setBand(UsersInput.creatingNewBand(command.getBandName()));
            send(command, datagramSocket, datagramPacket);
            command = receive(datagramSocket);
        }
        return command;
    }

    public static Commands resultTreatment(Commands command, DatagramSocket datagramSocket, DatagramPacket datagramPacket) throws Exception {
        command = creationTime(command, datagramSocket, datagramPacket);
        if (command.getResult() != null && command.getResult().equals("exit")) {
            System.exit(0);
        }
        if (command.getMasOfCommands().size() != 0) {
            for (Commands s : command.getMasOfCommands()) {
                if (s.getName() != null) {
                    s.setResult(null);
                    send(s, datagramSocket, datagramPacket);
                    command = resultTreatment(receive(datagramSocket), datagramSocket, datagramPacket);
                    System.out.println(command.getResult());
                    System.out.println(" ");
                } else {
                    System.out.println(s.getResult());
                }
            }
            command.setResult("Скрипт выполнен!");
        }
        if (command.getResult().equals("Waiting for name")) {
            System.out.println("Введите имя объекта");
            while (command.getBandName() == null) {
                command.setBandName(inputString());
            }
            command.setName("add");
            send(command, datagramSocket, datagramPacket);
            command = receive(datagramSocket);
            command = creationTime(command, datagramSocket, datagramPacket);
        }
        return command;
    }

    public static void send(Commands command, DatagramSocket datagramSocket, DatagramPacket datagramPacket) {
        try {
            command.setLogin(owner);
            b = serialize(command);
            System.arraycopy(b, 0, c, 0, b.length);
            datagramSocket.send(datagramPacket);
        } catch (Exception e){
            System.out.println("Неверный адрес");
        }
    }

    public static Commands receive(DatagramSocket datagramSocket) throws Exception {
        datagramSocket.setSoTimeout(timeout);
        //отправили, стараемся получить назад
        Commands commands = new Commands();
        try {
            DatagramPacket received = new DatagramPacket(c, c.length);
            datagramSocket.receive(received);
            commands = (Commands) deserialize(c);
            /*if (commands.getResult().substring(commands.getResult().length() - 1).equals("+")) {
                commands.setResult(commands.getResult().substring(0, commands.getResult().length() - 1));
                script = true;
            } else script = false;*/
            Arrays.fill(c, (byte) 0);
        } catch (SocketTimeoutException e) {
            System.out.println("Увы, ответа нет. Видимо, имеет смысл включить сервер или изменить порт/адрес сервера. Попробуйте еще раз");
            System.exit(0);
        }
        return commands;
    }


    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
            try (ObjectInputStream o = new ObjectInputStream(b)) {
                return o.readObject();
            }
        }
    }

    public static void authorization(DatagramSocket datagramSocket, DatagramPacket datagramPacket) throws Exception {
        Commands command = new Commands();
        String answer = "";
        while (!(answer.equals("Да") | answer.equals("Нет"))) {
            System.out.println("Желаете зарегистрироваться? Введите 'Да', если хотите и 'Нет' в ином случае");
            answer = inputString();
        }
        if (answer.equals("Да")) {
            registration(datagramSocket, datagramPacket);
        }
        answer = "";
        do {
            while (!(answer.equals("Да") | answer.equals("Нет"))) {
                System.out.println("Желаете авторизоваться? Введите 'Да' в таком случае. 'Нет' приведет к завершению программы");
                answer = inputString();
            }
            if (answer.equals("Нет")) {
                System.exit(0);
            } else {
                System.out.println("Введите логин");
                owner=inputString();
                System.out.println("Введите пароль");
                command.setPassword(cryptographer(inputString()));
                command.setName("checkUser");
                send(command, datagramSocket, datagramPacket);
                command = receive(datagramSocket);
                System.out.println(command.getResult());
            }
        } while (command.getResult().equals("Неверное имя пользователя или пароль. Попробуйте еще раз"));
        owner = command.getLogin();
    }

    public static void registration(DatagramSocket datagramSocket, DatagramPacket datagramPacket) throws Exception {
        Commands command = new Commands();
        do {
            System.out.println("Введите придуманный вами логин");
            owner = inputString();
            command.setName("checkLogin");
            send(command, datagramSocket, datagramPacket);
            command = receive(datagramSocket);
            System.out.println(command.getResult());
        } while (command.getResult().equals("Пользователь с таким логином уже существует"));
        System.out.println("Введите пароль");
        command.setPassword(cryptographer(inputString()));
        command.setName("addNewOwner");
        send(command, datagramSocket, datagramPacket);
        command = receive(datagramSocket);
        System.out.println(command.getResult());
    }

    public static String inputString() {
        Scanner scanner = new Scanner(System.in);
        String result = "";
        System.out.println("Введите exit, если хотите выйти из приложения");
        if (scanner.hasNext()) {
            result = scanner.nextLine();
            if(result.equals("exit")){
                System.exit(0);
            }
        } else {
            System.out.println("Не балуйтесь больше с ctrl+d");
            System.exit(0);
        }
        return result;
    }

    public static byte[] cryptographer(String password) {
        byte[] encryptedPassword = new byte[10000];
        try {
            MessageDigest digest = MessageDigest.getInstance("MD2");
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            encryptedPassword = digest.digest();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return encryptedPassword;
    }
}

