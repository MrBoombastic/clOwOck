document.addEventListener('DOMContentLoaded', () => {
    // Smooth scrolling for navigation links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                target.scrollIntoView({
                    behavior: 'smooth'
                });
            }
        });
    });

    // Mobile Hamburger Menu Toggle
    const hamburger = document.querySelector('.hamburger');
    const navLinks = document.querySelector('.nav-links');

    if (hamburger && navLinks) {
        hamburger.addEventListener('click', (e) => {
            e.stopPropagation();
            hamburger.classList.toggle('active');
            navLinks.classList.toggle('active');
        });

        // Close menu when clicking outside of it
        document.addEventListener('click', (e) => {
            if (navLinks.classList.contains('active') && !navLinks.contains(e.target) && !hamburger.contains(e.target)) {
                hamburger.classList.remove('active');
                navLinks.classList.remove('active');
            }
        });

        // Close menu when clicking any link
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                hamburger.classList.remove('active');
                navLinks.classList.remove('active');
            });
        });
    }

    // Intersection Observer for fade-in animations on feature cards
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    document.querySelectorAll('.feature-card').forEach(card => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        card.style.transition = 'opacity 0.6s ease-out, transform 0.6s ease-out';
        observer.observe(card);
    });

    // Carousel Buttons
    const track = document.querySelector('.carousel-track');
    const prevBtn = document.querySelector('.prev-btn');
    const nextBtn = document.querySelector('.next-btn');
    
    if (track && prevBtn && nextBtn) {
        const scrollAmount = 300; // Adjust based on how much you want to scroll per click

        prevBtn.addEventListener('click', () => {
            track.scrollBy({
                left: -scrollAmount,
                behavior: 'smooth'
            });
        });

        nextBtn.addEventListener('click', () => {
            track.scrollBy({
                left: scrollAmount,
                behavior: 'smooth'
            });
        });

        // Keyboard Navigation for Carousel
        document.addEventListener('keydown', (e) => {
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
            // Arrow keys belong to the lightbox while it is open
            if (document.querySelector('.lightbox[open]')) return;
            if (e.key === 'ArrowLeft') {
                track.scrollBy({
                    left: -scrollAmount,
                    behavior: 'smooth'
                });
            } else if (e.key === 'ArrowRight') {
                track.scrollBy({
                    left: scrollAmount,
                    behavior: 'smooth'
                });
            }
        });
    }

    // Screenshot Lightbox
    const lightbox = document.querySelector('.lightbox');
    const screenshots = Array.from(document.querySelectorAll('.carousel-img'));

    if (lightbox && screenshots.length) {
        const lightboxImg = lightbox.querySelector('.lightbox-img');
        const lightboxCaption = lightbox.querySelector('.lightbox-caption');
        let current = 0;

        const show = (index) => {
            current = (index + screenshots.length) % screenshots.length;
            const shot = screenshots[current];
            lightboxImg.src = shot.currentSrc || shot.src;
            lightboxImg.alt = shot.alt;
            lightboxCaption.textContent = `${shot.alt} - ${current + 1} / ${screenshots.length}`;
        };

        const open = (index) => {
            show(index);
            lightbox.showModal();
        };

        screenshots.forEach((shot, index) => {
            shot.tabIndex = 0;
            shot.setAttribute('role', 'button');
            shot.setAttribute('aria-label', `Open preview: ${shot.alt}`);
            shot.addEventListener('click', () => open(index));
            shot.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    open(index);
                }
            });
        });

        lightbox.querySelector('.lightbox-close').addEventListener('click', () => lightbox.close());
        lightbox.querySelector('.lightbox-prev').addEventListener('click', () => show(current - 1));
        lightbox.querySelector('.lightbox-next').addEventListener('click', () => show(current + 1));

        // Clicking the dark backdrop (outside the image and buttons) closes the preview
        lightbox.addEventListener('click', (e) => {
            if (e.target === lightbox || e.target.classList.contains('lightbox-figure')) lightbox.close();
        });

        lightbox.addEventListener('keydown', (e) => {
            if (e.key === 'ArrowLeft') show(current - 1);
            else if (e.key === 'ArrowRight') show(current + 1);
        });

        // Swipe left/right on touch screens
        let touchStartX = null;
        lightbox.addEventListener('touchstart', (e) => {
            touchStartX = e.touches[0].clientX;
        }, {passive: true});
        lightbox.addEventListener('touchend', (e) => {
            if (touchStartX === null) return;
            const dx = e.changedTouches[0].clientX - touchStartX;
            touchStartX = null;
            if (Math.abs(dx) > 50) show(current + (dx < 0 ? 1 : -1));
        });

        // Bring the screenshot that was shown last into view, so the carousel follows the preview.
        // Scroll only the track: scrollIntoView would also shift the page, whose overflow-x is hidden.
        lightbox.addEventListener('close', () => {
            const track = screenshots[current].parentElement;
            const shot = screenshots[current];
            track.scrollTo({
                left: shot.offsetLeft - track.offsetLeft - (track.clientWidth - shot.clientWidth) / 2,
                behavior: 'smooth'
            });
        });
    }
});
