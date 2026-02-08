package com.earzuhal.Controller;


import com.earzuhal.Model.User;
import com.earzuhal.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public List<User> getAllUsers(){
        return userService.getAllUsers();
    }
    @GetMapping("/users/{id}")
    public List<User> getUsersById(@PathVariable long id) {
        return userService.getUsersById(id);
    }


}
