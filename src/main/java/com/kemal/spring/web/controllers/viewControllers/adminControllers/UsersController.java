package com.kemal.spring.web.controllers.viewControllers.adminControllers;

import com.kemal.spring.domain.Role;
import com.kemal.spring.domain.User;
import com.kemal.spring.service.*;
import com.kemal.spring.web.dto.UserDto;
import com.kemal.spring.web.dto.UserUpdateDto;
import com.kemal.spring.web.paging.InitialPagingSizes;
import com.kemal.spring.web.paging.Pager;
import com.kemal.spring.web.searching.UserSearchParameters;
import com.kemal.spring.web.searching.UserSearchResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by Keno&Kemo on 20.11.2017..
 */


@Controller
@RequestMapping("/adminPage")
public class UsersController {
    private UserService userService;
    private RoleService roleService;
    private UserUpdateDtoService userUpdateDtoService;
    private UserDtoService userDtoService;

    public UsersController(UserService userService, RoleService roleService,
                           UserUpdateDtoService userUpdateDtoService,
                           UserDtoService userDtoService) {
        this.userService = userService;
        this.roleService = roleService;
        this.userUpdateDtoService = userUpdateDtoService;
        this.userDtoService = userDtoService;
    }

    /*
     * Get all users or search users if there are searching parameters
     */
    @GetMapping("/users")
    public String getUsers (Model model, UserSearchParameters userSearchParameters) {
        // Evaluate page size. If requeste parameter is null, return initial page size
        int evalPageSize = userSearchParameters.getPageSize().orElse(InitialPagingSizes.getInitialPageSize());

        // Evaluate page. If requested parameter is null or less than 0 (to prevent exception), return initial size.
        // Otherwise, return value of param. decreased by 1.
        int evalPage = (userSearchParameters.getPage().orElse(0) < 1) ? InitialPagingSizes.getInitialPage() : userSearchParameters.getPage().get() - 1;

        PageRequest pageRequest = PageRequest.of(evalPage, evalPageSize, new Sort(Sort.Direction.ASC, "id"));
        Page<UserDto> userDtoPage = new PageImpl<>(new ArrayList<>(), pageRequest, 0);
        UserSearchResult userSearchResult = new UserSearchResult(userDtoPage, false);

        //Empty search parameters
        if (!userSearchParameters.getPropertyValue().isPresent() || userSearchParameters.getPropertyValue().get().isEmpty())
            userSearchResult.setUserDtoPage(userDtoService.findAllPageable(pageRequest));

        // region Searching queries
        //==============================================================================================================
        else {
            userSearchResult = userDtoService.searchUsersByProperty(userSearchParameters.getUsersProperty().get(),
                                userSearchParameters.getPropertyValue().get(), userDtoPage, pageRequest);

            if(userSearchResult.isHasNumberFormatException()){
                Pager pager = new Pager(userSearchResult.getUserDtoPage().getTotalPages(), userSearchResult.getUserDtoPage().getNumber(),
                                        InitialPagingSizes.getButtonsToShow(), userSearchResult.getUserDtoPage().getTotalElements());
                model.addAttribute("numberFormatException", "Please enter valid number");
                model.addAttribute("users", userSearchResult.getUserDtoPage());
                model.addAttribute("pager", pager);
                return "adminPage/user/users";
            }

            if (userSearchResult.getUserDtoPage().getTotalElements() == 0) {
                userSearchResult.setUserDtoPage(userDtoService.findAllPageable(pageRequest));
                model.addAttribute("noMatches", true);
                model.addAttribute("users", userSearchResult.getUserDtoPage());
            }

            model.addAttribute("usersProperty", userSearchParameters.getUsersProperty().get());
            model.addAttribute("propertyValue", userSearchParameters.getPropertyValue().get());
        }
        //==============================================================================================================
        //endregion

        Pager pager = new Pager(userSearchResult.getUserDtoPage().getTotalPages(), userSearchResult.getUserDtoPage()
                .getNumber(), InitialPagingSizes.getButtonsToShow(), userSearchResult.getUserDtoPage()
                .getTotalElements());
        model.addAttribute("pager", pager);
        model.addAttribute("users", userSearchResult.getUserDtoPage());
        model.addAttribute("selectedPageSize", evalPageSize);
        model.addAttribute("pageSizes", InitialPagingSizes.getPageSizes());
        return "adminPage/user/users";
    }

    @GetMapping("/users/{id}")
    public String getEditUserForm(@PathVariable Long id, Model model) {
        UserUpdateDto userUpdateDto = userUpdateDtoService.findById(id);
        List<Role> allRoles = roleService.findAll();

        userUpdateDto.setRoles(userService.getAssignedRolesList(userUpdateDto));

        model.addAttribute("userUpdateDto", userUpdateDto);
        model.addAttribute("allRoles", allRoles);
        return "adminPage/user/editUser";
    }

    @PostMapping("/users/{id}")
    public String updateUser(Model model, @PathVariable Long id, @ModelAttribute("oldUser") @Valid final UserUpdateDto userUpdateDto,
                             BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        String formWithErrors = "adminPage/user/editUser";
        Optional<User> persistedUser = userService.findById(id);
        List<Role> allRoles = roleService.findAll();

        User emailAlreadyExists = userService.findByEmailAndIdNot(userUpdateDto.getEmail(), id);
        User usernameAlreadyExists = userService.findByUsernameAndIdNot(userUpdateDto.getUsername(), id);
        boolean hasErrors = false;

        if (emailAlreadyExists != null) {
            bindingResult.rejectValue("email", "emailAlreadyExists", "Oops!  There is already a user registered with the email provided.");
            hasErrors = true;
        }

        if (usernameAlreadyExists != null) {
            bindingResult.rejectValue("username", "usernameAlreadyExists", "Oops!  There is already a user registered with the username provided.");
            hasErrors = true;
        }

        if (bindingResult.hasErrors()) hasErrors = true;

        if (hasErrors) {
            model.addAttribute("userUpdateDto", userUpdateDto);
            model.addAttribute("rolesList", allRoles);
            model.addAttribute("org.springframework.validation.BindingResult.userUpdateDto", bindingResult);
            return formWithErrors;
        }
        else {
            userService.save(userService.getUpdatedUser(persistedUser.get(), userUpdateDto));
            redirectAttributes.addFlashAttribute("userHasBeenUpdated", true);
            return "redirect:/adminPage/users";
        }
    }

    @GetMapping("/users/newUser")
    public String getAddNewUserForm(Model model) {
        UserDto newUser = new UserDto();
        model.addAttribute("newUser", newUser);
        return "adminPage/user/newUser";
    }

    @PostMapping("/users/newUser")
    public String saveNewUser(Model model, @ModelAttribute("newUser") @Valid final UserDto newUser,
                              BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        User emailAlreadyExists = userService.findByEmail(newUser.getEmail());
        User usernameAlreadyExists = userService.findByUsername(newUser.getUsername());
        boolean hasErrors = false;
        String formWithErrors = "adminPage/user/newUser";

        if (emailAlreadyExists != null) {
            bindingResult.rejectValue("email", "emailAlreadyExists",
                    "Oops!  There is already a user registered with the email provided.");
            hasErrors = true;
        }

        if (usernameAlreadyExists != null) {
            bindingResult.rejectValue("username", "usernameAlreadyExists",
                    "Oops!  There is already a user registered with the username provided.");
            hasErrors = true;
        }

        if (bindingResult.hasErrors()) hasErrors = true;

        if (hasErrors) return formWithErrors;

        else {
            User user = userService.createNewAccount(newUser);
            user.setEnabled(true);

            userService.save(user);
            redirectAttributes.addFlashAttribute("userHasBeenSaved", true);
            return "redirect:/adminPage/users";
        }
    }

}
