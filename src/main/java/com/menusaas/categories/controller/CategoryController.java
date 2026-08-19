package com.menusaas.categories.controller;

import com.menusaas.categories.dto.CategoryRequest;
import com.menusaas.categories.dto.CategoryResponse;
import com.menusaas.categories.service.CategoryService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Categories", description = "Categorías del menú (siempre del restaurante del JWT)")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Listar mis categorías con paginación")
    @GetMapping
    public ApiResponse<Page<CategoryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(categoryService.listMine(
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "position"))));
    }

    @Operation(summary = "Obtener una categoría")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getMine(id));
    }

    @Operation(summary = "Crear categoría")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok("Categoría creada", categoryService.createMine(request));
    }

    @Operation(summary = "Actualizar categoría")
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok("Categoría actualizada", categoryService.updateMine(id, request));
    }

    @Operation(summary = "Eliminar categoría (y sus productos)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.deleteMine(id);
        return ApiResponse.ok("Categoría eliminada");
    }
}