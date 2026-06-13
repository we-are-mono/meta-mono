# Recovery shell prompt: orange host (matches the /etc/issue banner) so it's
# obvious you're in the rescue system, gray working directory. Only override
# interactive shells, where PS1 is set.
[ -n "$PS1" ] && PS1='\[\e[38;2;255;165;0m\]\u@\h\[\e[0m\]:\[\e[38;2;150;150;150m\]\w\[\e[0m\]\$ '
