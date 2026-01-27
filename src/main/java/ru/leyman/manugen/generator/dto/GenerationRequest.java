package ru.leyman.manugen.generator.dto;

import java.util.Map;

/**
 * Тело запроса генерации докуумента
 *
 * @param template полное имя файла шаблона
 * @param output полное имя генерируемого файла
 * @param params параметры генерации
 */
public record GenerationRequest(String template, String output,
                                Map<String, Object> params) {
}
