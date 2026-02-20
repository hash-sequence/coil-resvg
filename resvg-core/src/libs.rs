use std::sync::{Arc, OnceLock};
use tiny_skia::Pixmap;

uniffi::setup_scaffolding!();

/// Global cached fontdb instance (loaded once, shared across all SVG renderers)
static FONTDB_WITH_FONTS: OnceLock<Arc<fontdb::Database>> = OnceLock::new();

/// Global empty fontdb for SVGs without text
static FONTDB_EMPTY: OnceLock<Arc<fontdb::Database>> = OnceLock::new();

/// Check if SVG contains text elements using optimized detection
fn svg_has_text(svg_str: &str) -> bool {
    // Fast pre-check: if no text-related strings, skip XML parsing
    const KEYWORDS: &[&str] = &[
        "<text",
        ":text",
        "<tspan",
        ":tspan",
        "<foreignObject",
        ":foreignObject",
        "<altGlyph",
        ":altGlyph",
    ];

    KEYWORDS.iter().any(|&k| svg_str.contains(k))
}

/// Get empty fontdb (no system fonts loaded)
fn get_empty_fontdb() -> Arc<fontdb::Database> {
    FONTDB_EMPTY
        .get_or_init(|| Arc::new(fontdb::Database::new()))
        .clone()
}

/// Get or initialize the global fontdb instance with system fonts
fn get_fontdb_with_fonts() -> Arc<fontdb::Database> {
    FONTDB_WITH_FONTS
        .get_or_init(|| {
            let mut fontdb = fontdb::Database::new();
            fontdb.load_system_fonts();

            #[cfg(target_os = "android")]
            {
                fontdb.load_fonts_dir("/system/fonts");
                fontdb.set_sans_serif_family("Roboto");
                fontdb.set_serif_family("Noto Serif");
                fontdb.set_cursive_family("Roboto");
                fontdb.set_fantasy_family("Roboto");
                fontdb.set_monospace_family("Roboto Mono");
            }

            #[cfg(target_os = "ios")]
            {
                fontdb.load_fonts_dir("/System/Library/Fonts");
                fontdb.load_fonts_dir("/System/Library/Fonts/Core");
                fontdb.set_sans_serif_family("Helvetica Neue");
                fontdb.set_serif_family("Times New Roman");
                fontdb.set_monospace_family("Menlo");
                fontdb.set_cursive_family("Snell Roundhand");
                fontdb.set_fantasy_family("Papyrus");
            }

            Arc::new(fontdb)
        })
        .clone()
}

/// SVG size information
#[derive(uniffi::Record)]
pub struct SvgSize {
    pub width: f32,
    pub height: f32,
}

/// SVG render result, contains RGBA pixel data and size information
#[derive(uniffi::Record)]
pub struct SvgRenderResult {
    /// Image width (pixels)
    pub width: u32,
    /// Image height (pixels)
    pub height: u32,
    /// RGBA format pixel data
    pub pixels: Vec<u8>,
}

/// SVG rendering error
#[derive(Debug, uniffi::Error, thiserror::Error)]
pub enum SvgError {
    #[error("Failed to parse SVG: {msg}")]
    ParseError { msg: String },
    #[error("Render failed: {msg}")]
    RenderError { msg: String },
}

/// SVG renderer
#[derive(uniffi::Object)]
pub struct SvgRenderer {
    tree: usvg::Tree,
}

#[uniffi::export]
impl SvgRenderer {
    /// Create renderer from SVG data
    #[uniffi::constructor]
    pub fn from_data(svg_data: Vec<u8>) -> Result<Self, SvgError> {
        let svg_str = String::from_utf8(svg_data).map_err(|e| SvgError::ParseError {
            msg: format!("Invalid UTF-8 data: {}", e),
        })?;

        // Smart fontdb selection: trust string detection
        let fontdb = if svg_has_text(&svg_str) {
            get_fontdb_with_fonts()
        } else {
            get_empty_fontdb()
        };

        let mut options = usvg::Options::default();
        options.fontdb = fontdb;

        let tree = usvg::Tree::from_str(&svg_str, &options).map_err(|e| SvgError::ParseError {
            msg: format!("SVG parse failed: {}", e),
        })?;

        Ok(Self { tree })
    }

    /// Render SVG to specified size
    ///
    /// If width or height is 0, use SVG's original size
    pub fn render(&self, width: u32, height: u32) -> Result<SvgRenderResult, SvgError> {
        let size = self.tree.size();

        // If input size is 0, use SVG original size
        let target_width = if width == 0 {
            size.width().ceil() as u32
        } else {
            width
        };

        let target_height = if height == 0 {
            size.height().ceil() as u32
        } else {
            height
        };

        let mut pixmap =
            Pixmap::new(target_width, target_height).ok_or_else(|| SvgError::RenderError {
                msg: format!("Cannot create {}x{} pixmap", target_width, target_height),
            })?;

        // Calculate scale ratio
        let scale_x = target_width as f32 / size.width();
        let scale_y = target_height as f32 / size.height();

        let transform = tiny_skia::Transform::from_scale(scale_x, scale_y);

        resvg::render(&self.tree, transform, &mut pixmap.as_mut());

        let pixels = pixmap.data().to_vec();

        Ok(SvgRenderResult {
            width: target_width,
            height: target_height,
            pixels,
        })
    }

    /// Get SVG original size
    pub fn get_size(&self) -> SvgSize {
        let size = self.tree.size();
        SvgSize {
            width: size.width(),
            height: size.height(),
        }
    }
}

/// Directly render from SVG data to specified size (convenience function)
#[uniffi::export]
pub fn render_svg(svg_data: Vec<u8>, width: u32, height: u32) -> Result<SvgRenderResult, SvgError> {
    let renderer = SvgRenderer::from_data(svg_data)?;
    renderer.render(width, height)
}
