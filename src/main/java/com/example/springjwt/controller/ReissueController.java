package com.example.springjwt.controller;

import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springjwt.entity.RefreshEntity;
import com.example.springjwt.jwt.JWTUtil;
import com.example.springjwt.repository.RefreshRepository;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReissueController {

	private final JWTUtil jwtUtil;
	private final RefreshRepository refreshRepository;

	@PostMapping("/reissue")
	public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

		// 실제 프로젝트에서는 Service 단으로 빼기
		// get refresh token
		String refresh = null;
		Cookie[] cookies = request.getCookies();
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals("refresh")) {
				refresh = cookie.getValue();
			}
		}

		if (refresh == null) {
			// response status code
			return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
		}

		// expired check
		try {
			jwtUtil.isExpired(refresh);
		} catch (ExpiredJwtException e) {
			// response status code
			return new ResponseEntity<>("refresh token expired", HttpStatus.BAD_REQUEST);
		}

		// 토큰이 refresh인지 확인 (발급 시 페이로드에 명시)
		String category = jwtUtil.getCategory(refresh);

		if (!category.equals("refresh")) {
			// response status code
			return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
		}

		// DB에 저장되어 있는지 확인
		Boolean isExist = refreshRepository.existsByRefresh(refresh);
		if (!isExist) {
			// response body
			return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
		}

		String username = jwtUtil.getUsername(refresh);
		String role = jwtUtil.getRole(refresh);

		// make new JWT
		String newAccess = jwtUtil.createJwt("access", username, role, 600000L);
		String newRefresh = jwtUtil.createJwt("refresh", username, role, 86400000L);

		// DB에서 기존의 Refresh 토큰 삭제 후 새 Refresh 토큰 저장
		refreshRepository.deleteByRefresh(refresh);
		addRefreshEntity(username, newRefresh, 86400000L);

		// response
		response.setHeader("access", newAccess);
		response.addCookie(createCookie("refresh", newRefresh));

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private void addRefreshEntity(String username, String refresh, Long expiredMs) {

		Date date = new Date(System.currentTimeMillis() + expiredMs);

		RefreshEntity refreshEntity = new RefreshEntity();
		refreshEntity.setUsername(username);
		refreshEntity.setRefresh(refresh);
		refreshEntity.setExpiration(date.toString());

		refreshRepository.save(refreshEntity);
	}

	private Cookie createCookie(String key, String value) {

		Cookie cookie = new Cookie(key, value);
		cookie.setMaxAge(24*60*60);
		cookie.setSecure(true);
		//cookie.setPath("/");
		cookie.setHttpOnly(true);

		return cookie;
	}
}
