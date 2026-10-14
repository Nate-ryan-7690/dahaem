"""The doc generator on a made-up source tree (no repository code involved): the Java reader, the class pages, the
API reference and the use-case page.

Run from the repo root:  python3 -m unittest discover -s scripts/docs/tests
"""
import os
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import generate_docs as gd  # noqa: E402
import javaparse as jp  # noqa: E402

CONTROLLER = '''package de.dahaem.api;

import de.dahaem.shop.BasketService;
import de.dahaem.shop.Basket;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Baskets over HTTP. */
@RestController
@RequestMapping("/api/baskets")
public class BasketController {
    private final BasketService service;

    public BasketController(BasketService service) { this.service = service; }

    /** All baskets, optionally only those with at least {@code min} lines. */
    @GetMapping
    public List<BasketDto> all(@RequestParam(name = "min", defaultValue = "0") int min,
                               @RequestParam(name = "tags", required = false) List<String> tags) {
        return List.of();
    }

    /** Creates a basket. */
    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public BasketDto add(@PathVariable("id") int id, @RequestBody AddLineRequest request) {
        return null;
    }
}
'''
DTO = '''package de.dahaem.api;

/** A basket as the API shows it. */
public record BasketDto(int id, String name, Double total, Kind kind, List<LineDto> lines) {
    /** One line. */
    public record LineDto(int itemId, String unit) { }
    public enum Kind { WEEKLY, PARTY }
}
'''
REQUEST = '''package de.dahaem.api;
public record AddLineRequest(int itemId, double quantity) { }
'''
SERVICE = '''package de.dahaem.shop;

import java.util.List;

/**
 * Business logic for baskets.
 * Second paragraph.
 */
public class BasketService {
    private final BasketRepository repository;
    private static final String NAME = "a { brace";
    public BasketService(BasketRepository repository) { this.repository = repository; }
    /** Loads all baskets. */
    public List<Basket> all() { String s = "}"; return repository.find(); }
    private int hidden() { return 1; }
}
'''
REPO = '''package de.dahaem.shop;
import java.util.List;
public interface BasketRepository {
    List<Basket> find();
}
'''
MODEL = '''package de.dahaem.shop;
public class Basket extends Base implements Comparable<Basket> {
    private int id;
    private final java.util.Map<Integer, Line> lines = new java.util.HashMap<>() {{ }};
    public int getId() { return id; }
    public static Basket of(int id) { return null; }
    enum State { OPEN, CLOSED }
}
'''
BASE = "package de.dahaem.shop;\npublic abstract class Base { }\n"
LINE = "package de.dahaem.shop;\npublic record Line(int itemId) { }\n"


def write_tree(root):
    files = {"api/BasketController.java": CONTROLLER, "api/BasketDto.java": DTO, "api/AddLineRequest.java": REQUEST,
             "shop/BasketService.java": SERVICE, "shop/BasketRepository.java": REPO, "shop/Basket.java": MODEL,
             "shop/Base.java": BASE, "shop/Line.java": LINE}
    for rel, text in files.items():
        path = os.path.join(root, "src", "main", "java", "de", "dahaem", rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)


class JavaParseTest(unittest.TestCase):
    def parse(self, text):
        return jp.parse_file("X.java", text)

    def test_class_members_and_javadoc(self):
        t = {x.name: x for x in self.parse(SERVICE)}["BasketService"]
        self.assertEqual("class", t.kind)
        self.assertTrue(t.doc.startswith("Business logic for baskets."))
        self.assertEqual("Business logic for baskets.", jp.first_sentence(t.doc))
        kinds = [(m.kind, m.name, m.visibility) for m in t.members]
        self.assertIn(("field", "repository", "private"), kinds)
        self.assertIn(("constructor", "BasketService", "public"), kinds)
        self.assertIn(("method", "all", "public"), kinds)
        self.assertIn(("method", "hidden", "private"), kinds)
        self.assertIn(("field", "NAME", "private"), kinds, "a brace inside a string does not break the scan")
        all_ = next(m for m in t.members if m.name == "all")
        self.assertEqual("List<Basket>", all_.type)
        self.assertEqual("Loads all baskets.", all_.doc)

    def test_records_enums_nested_and_inheritance(self):
        types = {x.local_name: x for x in self.parse(DTO)}
        self.assertEqual({"BasketDto", "BasketDto.LineDto", "BasketDto.Kind"}, set(types))
        self.assertEqual(["id", "name", "total", "kind", "lines"],
                         [m.name for m in types["BasketDto"].members if m.kind == "component"])
        self.assertEqual(["WEEKLY", "PARTY"], [m.name for m in types["BasketDto.Kind"].members])
        self.assertEqual("One line.", types["BasketDto.LineDto"].doc)
        basket = {x.local_name: x for x in self.parse(MODEL)}["Basket"]
        self.assertEqual(["Base"], basket.extends)
        self.assertEqual(["Comparable<Basket>"], basket.implements)
        self.assertEqual(1, len([m for m in basket.members if m.name == "lines"]), "initializer braces skipped")
        self.assertTrue(next(m for m in basket.members if m.name == "of").static)

    def test_interface_methods_are_public(self):
        t = self.parse(REPO)[0]
        self.assertEqual("interface", t.kind)
        self.assertEqual("public", t.members[0].visibility)


class GeneratorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        write_tree(self.tmp)
        self.model = gd.Model(os.path.join(self.tmp, "src", "main", "java"))
        self.files = gd.build(self.model)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def test_every_type_gets_its_own_diagram(self):
        page = self.files["docs/classes/shop.md"]
        for name in ("Base", "Basket", "BasketRepository", "BasketService", "Line"):
            self.assertIn(f"\n## {name}\n", page)
        self.assertEqual(len(self.model.by_package["shop"]) + 1, page.count("```mermaid"))   # + the overview
        self.assertIn("Base <|-- Basket".replace("Base", "shop_Base").replace("Basket", 'shop_Basket'), page)
        self.assertIn("shop_BasketService --> shop_BasketRepository", page)
        self.assertIn("<<interface>>", page)
        self.assertIn("BasketDto.LineDto", self.files["docs/classes/api.md"])
        self.assertIn("api_BasketDto *-- api_BasketDto_LineDto", self.files["docs/classes/api.md"])

    def test_class_index_lists_packages_and_the_import_graph(self):
        index = self.files["docs/classes/README.md"]
        self.assertIn("api -->|2| shop", index)
        self.assertIn("[`shop`](shop.md)", index)
        self.assertIn("[`Basket`](shop.md#basket)", index)

    def test_api_reference(self):
        api = self.files["docs/API.md"]
        self.assertIn("#### GET /api/baskets", api)
        self.assertIn("#### POST /api/baskets/{id}/lines", api)
        self.assertIn("| `min` | `int` | no | `0` |", api)
        self.assertIn("| `tags` | `List<String>` | no |  |", api)
        self.assertIn("**Answer** 201 `BasketDto`", api)
        self.assertIn("**Request body** `AddLineRequest`", api)
        self.assertIn('"quantity": 0.0', api)
        self.assertIn('"kind": "WEEKLY|PARTY"', api)
        self.assertIn("### BasketDto.LineDto", api)
        self.assertIn("(#get-apibaskets)", api)
        self.assertIn("X-Internal-Token", api)

    def test_use_cases_page(self):
        page = self.files["docs/USE_CASES.md"]
        self.assertIn("GET /api/baskets", page)
        self.assertEqual(2, page.count("```mermaid"))

    def test_github_anchor(self):
        self.assertEqual("get-apirecipesid", gd.gh_anchor("GET /api/recipes/{id}"))
        self.assertEqual("currentuserdtohouseholddto", gd.gh_anchor("CurrentUserDto.HouseholdDto"))

    def test_check_mode(self):
        self.assertEqual(1, gd.main(["--root", self.tmp, "--check"]))        # nothing written yet
        self.assertEqual(0, gd.main(["--root", self.tmp]))
        self.assertEqual(0, gd.main(["--root", self.tmp, "--check"]))

    def test_output_does_not_depend_on_the_hash_seed(self):
        import subprocess
        script = os.path.join(os.path.dirname(HERE), "generate_docs.py")
        outputs = []
        for seed in ("1", "2"):
            subprocess.run([sys.executable, script, "--root", self.tmp], check=True, capture_output=True,
                           env=dict(os.environ, PYTHONHASHSEED=seed))
            with open(os.path.join(self.tmp, "docs", "classes", "shop.md"), encoding="utf-8") as f:
                outputs.append(f.read())
        self.assertEqual(outputs[0], outputs[1])


if __name__ == "__main__":
    unittest.main()
