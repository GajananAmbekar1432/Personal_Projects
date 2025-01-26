package com.ecom.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Category;
import com.ecom.model.Product;
import com.ecom.model.ProductOrder;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.OrderService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.OrderStatus;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/seller")
public class SellerController {

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private ProductService productService;

	@Autowired
	private UserService userService;

	@Autowired
	private CartService cartService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
			Integer countCart = cartService.getCountCart(userDtls.getId());
			m.addAttribute("countCart", countCart);
		}

		List<Category> allActiveCategory = categoryService.getAllActiveCategory();
		m.addAttribute("categorys", allActiveCategory);
	}

	@GetMapping("/")
	public String index() {
		return "seller/index";
	}

	@GetMapping("/loadAddProduct")
	public String loadAddProduct(Model m, Principal principal) {
		List<Category> categories = categoryService.getAllCategory();
		if (principal != null) {
			String email = principal.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			String shopName = userDtls.getShopName(); // Fetch shop name for use in the form
			m.addAttribute("user", userDtls); // Add the user details to the model
			m.addAttribute("shopName", shopName); // Add the shop name to the model
		}
		m.addAttribute("categories", categories);
		return "seller/add_product";
	}

	@PostMapping("/saveProduct")
	public String saveProduct(@ModelAttribute Product product, @RequestParam("imageUrl") String imageUrl,
			HttpSession session) {

		product.setImage(imageUrl); // Use the image URL directly
		product.setDiscount(0);
		product.setDiscountPrice(product.getPrice());

		Product saveProduct = productService.saveProduct(product);

		if (!ObjectUtils.isEmpty(saveProduct)) {
			session.setAttribute("succMsg", "Product saved successfully");
		} else {
			session.setAttribute("errorMsg", "Something went wrong on the server");
		}

		return "redirect:/seller/loadAddProduct";
	}

	@GetMapping("/products")
	public String loadViewProduct(Model m,
			@RequestParam(defaultValue = "") String ch,
			@RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
			@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
			Principal principal) {

		// Retrieve logged-in user's details
		String email = principal.getName();
		UserDtls user = userService.getUserByEmail(email);

		if (user == null) {
			m.addAttribute("errorMsg", "Unable to retrieve seller's shop information.");
			return "seller/products"; // Redirect to an appropriate error view
		}

		String shopName = user.getShopName();
		Page<Product> page = null;

		// Filter products by `shopName` and optional search term
		if (ch != null && !ch.isEmpty()) {
			page = productService.searchProductsByShopNameWithPagination(shopName, ch, pageNo, pageSize);
		} else {
			page = productService.getProductsByShopNameWithPagination(shopName, pageNo, pageSize);
		}

		// Add data to the model
		m.addAttribute("products", page.getContent());
		m.addAttribute("pageNo", page.getNumber());
		m.addAttribute("pageSize", pageSize);
		m.addAttribute("totalElements", page.getTotalElements());
		m.addAttribute("totalPages", page.getTotalPages());
		m.addAttribute("isFirst", page.isFirst());
		m.addAttribute("isLast", page.isLast());

		return "seller/products";
	}

	@GetMapping("/deleteProduct/{id}")
	public String deleteProduct(@PathVariable int id, HttpSession session) {
		Boolean deleteProduct = productService.deleteProduct(id);
		if (deleteProduct) {
			session.setAttribute("succMsg", "Product delete success");
		} else {
			session.setAttribute("errorMsg", "Something wrong on server");
		}
		return "redirect:/seller/products";
	}

	@GetMapping("/editProduct/{id}")
	public String editProduct(@PathVariable int id, Model m) {
		m.addAttribute("product", productService.getProductById(id));
		m.addAttribute("categories", categoryService.getAllCategory());
		return "seller/edit_product";
	}

	@PostMapping("/updateProduct")
	public String updateProduct(@ModelAttribute Product product, @RequestParam("imageUrl") String imageUrl,
			HttpSession session) {

		if (product.getDiscount() < 0 || product.getDiscount() > 100) {
			session.setAttribute("errorMsg", "Invalid discount");
		} else {
			product.setImage(imageUrl); // Set the new image URL
			Product updatedProduct = productService.updateProduct(product);
			if (!ObjectUtils.isEmpty(updatedProduct)) {
				session.setAttribute("succMsg", "Product updated successfully");
			} else {
				session.setAttribute("errorMsg", "Something went wrong on the server");
			}
		}

		return "redirect:/seller/editProduct/" + product.getId();
	}

	@GetMapping("/orders")
	public String getAllOrders(Model m, @RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
			@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize, Principal principal) {
		// List<ProductOrder> allOrders = orderService.getAllOrders();
		// m.addAttribute("orders", allOrders);
		// m.addAttribute("srch", false);

		// Fetch logged-in seller's email and user details
		String email = principal.getName();
		UserDtls user = userService.getUserByEmail(email);

		// Check if the user or shop name is null
		if (user == null || user.getShopName() == null) {
			m.addAttribute("errorMsg", "Unable to retrieve seller's shop information.");
			return "seller/orders"; // Ensure consistent path (remove leading slash for Thymeleaf resolution)
		}

		String shopName = user.getShopName();
		Page<ProductOrder> page = orderService.getOrdersByShopName(shopName, pageNo, pageSize);

		m.addAttribute("orders", page.getContent());
		m.addAttribute("srch", false);

		m.addAttribute("pageNo", page.getNumber());
		m.addAttribute("pageSize", pageSize);
		m.addAttribute("totalElements", page.getTotalElements());
		m.addAttribute("totalPages", page.getTotalPages());
		m.addAttribute("isFirst", page.isFirst());
		m.addAttribute("isLast", page.isLast());

		return "/seller/orders";
	}

	@PostMapping("/update-order-status")
	public String updateOrderStatus(@RequestParam Integer id, @RequestParam Integer st, HttpSession session) {

		OrderStatus[] values = OrderStatus.values();
		String status = null;

		for (OrderStatus orderSt : values) {
			if (orderSt.getId().equals(st)) {
				status = orderSt.getName();
			}
		}

		ProductOrder updateOrder = orderService.updateOrderStatus(id, status);

		try {
			commonUtil.sendMailForProductOrder(updateOrder, status);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!ObjectUtils.isEmpty(updateOrder)) {
			session.setAttribute("succMsg", "Status Updated");
		} else {
			session.setAttribute("errorMsg", "status not updated");
		}
		return "redirect:/seller/orders";
	}

	@GetMapping("/search-order")
	public String searchProduct(@RequestParam String orderId, Model m, HttpSession session,
			@RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
			@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize, Principal principal) {

		String email = principal.getName();
		UserDtls user = userService.getUserByEmail(email);

		// Check if the user or shop name is null
		if (user == null || user.getShopName() == null) {
			m.addAttribute("errorMsg", "Unable to retrieve seller's shop information.");
			return "seller/orders"; // Ensure consistent path (remove leading slash for Thymeleaf resolution)
		}

		String shopName = user.getShopName();

		if (orderId != null && orderId.length() > 0) {

			ProductOrder order = orderService.getOrdersByOrderId(orderId.trim());

			if (ObjectUtils.isEmpty(order)) {
				session.setAttribute("errorMsg", "Incorrect orderId");
				m.addAttribute("orderDtls", null);
			} else {
				m.addAttribute("orderDtls", order);
			}

			m.addAttribute("srch", true);
		} else {
			// List<ProductOrder> allOrders = orderService.getAllOrders();
			// m.addAttribute("orders", allOrders);
			// m.addAttribute("srch", false);

			Page<ProductOrder> page = orderService.getOrdersByShopName(shopName, pageNo, pageSize);
			m.addAttribute("orders", page);
			m.addAttribute("srch", false);

			m.addAttribute("pageNo", page.getNumber());
			m.addAttribute("pageSize", pageSize);
			m.addAttribute("totalElements", page.getTotalElements());
			m.addAttribute("totalPages", page.getTotalPages());
			m.addAttribute("isFirst", page.isFirst());
			m.addAttribute("isLast", page.isLast());

		}
		return "/seller/orders";

	}

	@GetMapping("/profile")
	public String profile() {
		return "/seller/profile";
	}

	@PostMapping("/update-profile")
	public String updateProfile(@ModelAttribute UserDtls user, @RequestParam MultipartFile img, HttpSession session) {
		UserDtls updateUserProfile = userService.updateUserProfile(user, img);
		if (ObjectUtils.isEmpty(updateUserProfile)) {
			session.setAttribute("errorMsg", "Profile not updated");
		} else {
			session.setAttribute("succMsg", "Profile Updated");
		}
		return "redirect:/seller/profile";
	}

	@PostMapping("/change-password")
	public String changePassword(@RequestParam String newPassword, @RequestParam String currentPassword, Principal p,
			HttpSession session) {
		UserDtls loggedInUserDetails = commonUtil.getLoggedInUserDetails(p);

		boolean matches = passwordEncoder.matches(currentPassword, loggedInUserDetails.getPassword());

		if (matches) {
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "Password not updated !! Error in server");
			} else {
				session.setAttribute("succMsg", "Password Updated sucessfully");
			}
		} else {
			session.setAttribute("errorMsg", "Current Password incorrect");
		}

		return "redirect:/seller/profile";
	}

}
