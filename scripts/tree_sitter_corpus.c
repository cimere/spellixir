/* Test-only bridge. The pinned runtime and grammar are built outside the plugin artifact. */
#include <tree_sitter/api.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

const TSLanguage *tree_sitter_elixir(void);

static void emit_string(const char *text) {
    putchar('"');
    for (const unsigned char *p = (const unsigned char *)text; *p; p++) {
        if (*p == '"' || *p == '\\') putchar('\\');
        if (*p < 0x20) printf("\\u%04x", *p);
        else putchar(*p);
    }
    putchar('"');
}

static void emit_node(TSNode node, int *first) {
    if (ts_node_is_named(node) || ts_node_is_missing(node)) {
        if (!*first) printf(",\n");
        *first = 0;
        printf("    [%u,%u,", ts_node_start_byte(node), ts_node_end_byte(node));
        emit_string(ts_node_type(node));
        printf(",%s]", ts_node_is_missing(node) ? "true" : "false");
    }
    for (uint32_t i = 0; i < ts_node_child_count(node); i++) {
        emit_node(ts_node_child(node, i), first);
    }
}

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    FILE *file = fopen(argv[1], "rb");
    if (!file) return 2;
    if (fseek(file, 0, SEEK_END) != 0) return 2;
    long size = ftell(file);
    if (size < 0 || size > 10 * 1024 * 1024 || fseek(file, 0, SEEK_SET) != 0) return 2;
    char *source = malloc((size_t)size + 1);
    if (!source || fread(source, 1, (size_t)size, file) != (size_t)size) return 2;
    fclose(file);
    TSParser *parser = ts_parser_new();
    if (!ts_parser_set_language(parser, tree_sitter_elixir())) return 3;
    TSTree *tree = ts_parser_parse_string(parser, NULL, source, (uint32_t)size);
    if (!tree) return 4;
    TSNode root = ts_tree_root_node(tree);
    printf("{\n  \"hasError\": %s,\n  \"nodes\": [\n", ts_node_has_error(root) ? "true" : "false");
    int first = 1;
    emit_node(root, &first);
    printf("\n  ]\n}\n");
    ts_tree_delete(tree);
    ts_parser_delete(parser);
    free(source);
    return 0;
}
