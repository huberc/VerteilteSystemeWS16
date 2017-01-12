package chatserver;

import model.User;
import util.Config;

import java.net.Socket;
import java.util.Collection;
import java.util.TreeMap;

public class Usermanager {

    private TreeMap<String,User> users;

    public Collection<User> getUsers(){
        return users.values();
    }

    /**
     * Loads the users from the user Config file
     */
    public void loadFromConfig(){

        users = new TreeMap<>();

        //Read out user from the properties file
        Config userConfig = new Config("user");
        Integer id = 1;
        for(String key : userConfig.listKeys()){
            String password = userConfig.getString(key);
            String username = key.substring(0,key.indexOf(".password"));
            User user = new User(id++,username,password);
            users.put(username,user);
        }
    }

    /**
     * Delivers the user with the given name or null if not found
     * @param name the searched username
     * @return the user or null if not found
     */
    public User getByName(String name){
        return users.get(name);
    }

    /**
     * Delivers the user with the given socket
     * @param socket the socketAddress
     * @return the user or null if no user with this socket is found
     */
    public User getLoggedInUserBySocket(Socket socket){

        if(socket == null) return null;

        for(User u : getUsers()){
            if(u.getSocket() != null && u.getSocket().equals(socket) && u.isLoggedIn()){
                return u;
            }
        }

        return null;
    }

    public User getUserBySocket(Socket socket){
        if(socket == null) return null;

        for(User u : getUsers()){
            if(u.getSocket() != null && u.getSocket().equals(socket)){
                return u;
            }
        }

        return null;
    }
}
